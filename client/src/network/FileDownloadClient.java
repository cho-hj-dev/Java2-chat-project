package network;

import app.Application;
import dto.request.FileGetRequest;
import dto.type.DtoType;

import java.awt.Component;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class FileDownloadClient {

    public static final FileDownloadClient INSTANCE =
            new FileDownloadClient();

    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int CHUNK_BYTES = 16 * 1024;

    private Download active;

    private FileDownloadClient() {
    }

    private static class Download {
        final long attachmentId;
        final String fileName;
        final Path target;
        final Component owner;

        int expectedSize = -1;
        String expectedHash;
        int nextSequence;
        ByteArrayOutputStream data;

        Download(
                long attachmentId, String fileName,
                Path target, Component owner) {
            this.attachmentId = attachmentId;
            this.fileName = fileName;
            this.target = target;
            this.owner = owner;
        }
    }

    /** 사용자가 저장할 위치를 고른 뒤 서버에 파일을 요청한다. */
    public void promptSave(
            Component owner, long attachmentId, String fileName) {
        synchronized (this) {
            if (active != null) {
                JOptionPane.showMessageDialog(
                        owner, "이미 다운로드 중인 파일이 있습니다.");
                return;
            }
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("받은 파일 저장");
        chooser.setSelectedFile(new File(fileName));

        if (chooser.showSaveDialog(owner)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path target = chooser.getSelectedFile()
                .toPath().toAbsolutePath();

        if (Files.isDirectory(target)) {
            JOptionPane.showMessageDialog(
                    owner, "파일 이름까지 지정해 주세요.");
            return;
        }

        if (Files.exists(target)) {
            int answer = JOptionPane.showConfirmDialog(
                    owner,
                    "같은 이름의 파일이 있습니다. 덮어쓸까요?",
                    "파일 저장",
                    JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        synchronized (this) {
            if (active != null) {
                JOptionPane.showMessageDialog(
                        owner, "이미 다운로드 중인 파일이 있습니다.");
                return;
            }

            active = new Download(
                    attachmentId, fileName, target, owner);
        }

        if (!Application.sender.sendMessage(
                new FileGetRequest(attachmentId))) {
            fail("파일 다운로드 요청을 보내지 못했습니다.");
        }
    }

    /**
     * MessageReceiver의 수신 스레드에서 호출한다.
     * 큰 파일 조각을 Swing 화면 스레드에서 처리하지 않는다.
     */
    public synchronized void onMessage(
            DtoType type, String message) {
        if (active == null) {
            return;
        }

        try {
            switch (type) {
                case FILE_DOWNLOAD_BEGIN -> begin(message);
                case FILE_DOWNLOAD_CHUNK -> append(message);
                case FILE_DOWNLOAD_END -> finish(message);
                default -> {
                    // 파일 다운로드 응답이 아니면 처리하지 않는다.
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            fail("파일을 저장하지 못했습니다: " + error.getMessage());
        }
    }

    private void begin(String message) {
        // ID,Base64(파일명),MIME,크기,SHA-256
        String[] values = message.split(",", 5);
        if (values.length != 5
                || Long.parseLong(values[0]) != active.attachmentId) {
            throw new IllegalArgumentException("파일 정보가 잘못됐습니다.");
        }

        String name = new String(
                Base64.getDecoder().decode(values[1]),
                StandardCharsets.UTF_8);
        int size = Integer.parseInt(values[3]);

        if (!name.equals(active.fileName)
                || size < 0 || size > MAX_BYTES
                || !values[4].matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("파일 정보가 잘못됐습니다.");
        }

        active.expectedSize = size;
        active.expectedHash = values[4];
        active.data = new ByteArrayOutputStream(
                Math.min(size, 64 * 1024));
    }

    private void append(String message) {
        // ID,조각번호,Base64(파일 바이트)
        String[] values = message.split(",", 3);
        if (values.length != 3
                || active.data == null
                || Long.parseLong(values[0]) != active.attachmentId
                || Integer.parseInt(values[1]) != active.nextSequence
                || values[2].length() > 22_000) {
            throw new IllegalArgumentException("파일 조각이 잘못됐습니다.");
        }

        byte[] chunk = Base64.getDecoder().decode(values[2]);

        if (chunk.length == 0
                || chunk.length > CHUNK_BYTES
                || active.data.size() + chunk.length
                        > active.expectedSize) {
            throw new IllegalArgumentException("파일 조각 크기가 잘못됐습니다.");
        }

        active.data.write(chunk, 0, chunk.length);
        active.nextSequence++;
    }

    private void finish(String message) throws IOException {
        // 성공: ID,OK / 실패: ID,ERROR,사유
        String[] values = message.split(",", 3);
        if (values.length < 2
                || Long.parseLong(values[0]) != active.attachmentId) {
            throw new IllegalArgumentException("완료 응답이 잘못됐습니다.");
        }

        if (!"OK".equals(values[1])) {
            throw new IOException("서버에서 파일을 제공하지 못했습니다.");
        }

        if (active.data == null) {
            throw new IOException("파일 정보가 도착하지 않았습니다.");
        }

        byte[] bytes = active.data.toByteArray();
        if (bytes.length != active.expectedSize
                || !sha256(bytes).equals(active.expectedHash)) {
            throw new IOException("파일 크기 또는 해시가 일치하지 않습니다.");
        }

        Download completed = active;
        Path temporary = Files.createTempFile(
                completed.target.getParent(),
                "chat-download-", ".part");

        try {
            // 검사가 끝난 내용만 최종 저장 위치로 옮긴다.
            Files.write(temporary, bytes);
            Files.move(
                    temporary,
                    completed.target,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }

        active = null;
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        completed.owner,
                        "파일 저장 완료:\n" + completed.target));
    }

    public synchronized void connectionClosed() {
        if (active != null) {
            fail("서버 연결이 종료되어 다운로드가 중단됐습니다.");
        }
    }

    private void fail(String message) {
        Download failed = active;
        active = null;

        if (failed != null) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            failed.owner,
                            message,
                            "파일 다운로드 오류",
                            JOptionPane.ERROR_MESSAGE));
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(
                    "SHA-256을 사용할 수 없습니다.", error);
        }
    }
}