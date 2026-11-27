package network;

import app.Application;
import dto.request.FileChunkRequest;
import dto.request.FileEndRequest;
import dto.request.FileStartRequest;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class FileUploadClient {

    public static final FileUploadClient INSTANCE = new FileUploadClient();
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    // 한 소켓에서 동시에 업로드하는 파일은 하나로 제한한다.
    private volatile Upload active;

    private FileUploadClient() {
    }

    private static class Upload {
        final Component owner;
        final String roomName;
        final Path path;
        volatile byte[] bytes;

        Upload(Component owner, String roomName, Path path) {
            this.owner = owner;
            this.roomName = roomName;
            this.path = path;
        }
    }

    public void start(Component owner, String roomName, Path path) {
        if (active != null) {
            JOptionPane.showMessageDialog(
                    owner, "이미 전송 중인 파일이 있습니다.");
            return;
        }

        Upload upload = new Upload(owner, roomName, path);
        active = upload;
        runInBackground(() -> prepareAndStart(upload));
    }

    private void prepareAndStart(Upload upload) {
        try {
            if (!Files.isRegularFile(upload.path)) {
                throw new IOException("파일을 선택해 주세요.");
            }

            long size = Files.size(upload.path);
            if (size > MAX_FILE_BYTES) {
                throw new IOException("파일은 최대 10MB까지 전송할 수 있습니다.");
            }

            // 읽는 동안 파일 크기가 바뀌었을 수도 있어 한 번 더 확인한다.
            byte[] bytes = Files.readAllBytes(upload.path);
            if (bytes.length > MAX_FILE_BYTES) {
                throw new IOException("파일은 최대 10MB까지 전송할 수 있습니다.");
            }

            String mimeType = Files.probeContentType(upload.path);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
            mimeType = mimeType.toLowerCase(Locale.ROOT);

            upload.bytes = bytes;

            // 먼저 파일 정보만 전송하고 서버의 FILE_READY를 기다린다.
            boolean sent = Application.sender.sendMessage(
                    new FileStartRequest(
                            upload.roomName,
                            upload.path.getFileName().toString(),
                            mimeType,
                            bytes.length,
                            sha256(bytes)));

            if (!sent) {
                throw new IOException("파일 시작 요청을 보내지 못했습니다.");
            }

        } catch (IOException | IllegalArgumentException error) {
            fail(upload, error.getMessage());
        }
    }

    /** MessageReceiver가 서버의 FILE_READY 응답을 받으면 호출한다. */
    public void onReady(String status) {
        Upload upload = active;
        if (upload == null) {
            return;
        }

        if (!"OK".equals(status)) {
            fail(upload, "서버가 파일 전송을 허용하지 않았습니다: " + status);
            return;
        }

        runInBackground(() -> sendChunks(upload));
    }

    private void sendChunks(Upload upload) {
        try {
            byte[] bytes = upload.bytes;
            if (bytes == null) {
                throw new IOException("전송할 파일을 읽지 못했습니다.");
            }

            int sequence = 0;
            for (int offset = 0;
                    offset < bytes.length;
                    offset += FileChunkRequest.CHUNK_BYTES) {
                // 서버가 오류를 알려 업로드가 끝났다면 나머지는 보내지 않는다.
                if (active != upload) {
                    return;
                }

                int end = Math.min(
                        offset + FileChunkRequest.CHUNK_BYTES,
                        bytes.length);
                byte[] chunk = Arrays.copyOfRange(bytes, offset, end);

                if (!Application.sender.sendMessage(
                        new FileChunkRequest(sequence, chunk))) {
                    throw new IOException("파일 조각을 보내지 못했습니다.");
                }
                sequence++;
            }

            // 빈 파일은 조각 없이 곧바로 완료 요청을 보낸다.
            if (active == upload
                    && !Application.sender.sendMessage(new FileEndRequest())) {
                throw new IOException("파일 완료 요청을 보내지 못했습니다.");
            }

        } catch (IOException | IllegalArgumentException error) {
            fail(upload, error.getMessage());
        }
    }

    /** MessageReceiver가 DB 저장 결과를 받으면 호출한다. */
    public void onResult(String result) {
        Upload upload = active;
        if (upload == null) {
            return;
        }

        active = null;

        if (result.startsWith("OK,")) {
            JOptionPane.showMessageDialog(
                    upload.owner, "파일이 서버 DB에 저장됐습니다.");
        } else {
            JOptionPane.showMessageDialog(
                    upload.owner, "파일 전송에 실패했습니다: " + result);
        }
    }

    private void fail(Upload upload, String message) {
        if (active != upload) {
            return;
        }

        active = null;
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        upload.owner, message, "파일 전송 오류",
                        JOptionPane.ERROR_MESSAGE));
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

    private void runInBackground(Runnable task) {
        Thread thread = new Thread(task, "chat-file-upload");
        thread.setDaemon(true);
        thread.start();
    }
}