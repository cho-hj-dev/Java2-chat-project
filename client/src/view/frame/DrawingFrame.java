package view.frame;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

public class DrawingFrame extends JFrame {

    private static final int CANVAS_WIDTH = 600;
    private static final int CANVAS_HEIGHT = 350;

    private final BufferedImage drawing = new BufferedImage(
            CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
    private final Consumer<BufferedImage> onSend;
    private final JPanel canvasPanel;

    private Color penColor = Color.BLACK;
    private int penWidth = 4;
    private int lastX;
    private int lastY;

    public DrawingFrame(JFrame owner, Consumer<BufferedImage> onSend) {
        this.onSend = onSend;

        setTitle("그림판");
        setLayout(null);
        setSize(650, 500);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        clearCanvas();

        canvasPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                graphics.drawImage(drawing, 0, 0, null);
            }
        };
        canvasPanel.setBounds(20, 20, CANVAS_WIDTH, CANVAS_HEIGHT);

        canvasPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                lastX = event.getX();
                lastY = event.getY();
                drawLine(lastX, lastY, lastX, lastY);
            }
        });

        canvasPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                int x = event.getX();
                int y = event.getY();
                drawLine(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
            }
        });

        add(canvasPanel);

        JButton colorButton = new JButton("색 선택");
        colorButton.setBounds(20, 390, 105, 35);
        colorButton.setBackground(penColor);
        colorButton.addActionListener(event -> {
            Color selected = JColorChooser.showDialog(
                    this, "펜 색상 선택", penColor);
            if (selected != null) {
                penColor = selected;
                colorButton.setBackground(selected);
            }
        });
        add(colorButton);

        JLabel widthLabel = new JLabel("굵기");
        widthLabel.setBounds(140, 390, 45, 35);
        add(widthLabel);

        JSpinner widthSpinner = new JSpinner(
                new SpinnerNumberModel(4, 1, 24, 1));
        widthSpinner.setBounds(185, 390, 65, 35);
        widthSpinner.addChangeListener(
                event -> penWidth = (Integer) widthSpinner.getValue());
        add(widthSpinner);

        JButton clearButton = new JButton("지우기");
        clearButton.setBounds(265, 390, 105, 35);
        clearButton.addActionListener(event -> {
            int choice = JOptionPane.showConfirmDialog(
                    this, "그린 내용을 모두 지울까요?",
                    "그림 지우기", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                clearCanvas();
                canvasPanel.repaint();
            }
        });
        add(clearButton);

        JButton saveButton = new JButton("PNG 저장");
        saveButton.setBounds(385, 390, 105, 35);
        saveButton.addActionListener(event -> savePng());
        add(saveButton);

        JButton sendButton = new JButton("채팅 전송");
        sendButton.setBounds(505, 390, 115, 35);
        sendButton.setEnabled(onSend != null);
        sendButton.addActionListener(event -> {
            if (onSend != null) {
                onSend.accept(copyDrawing());
            }
        });
        add(sendButton);

        setVisible(true);
    }

    private void drawLine(int fromX, int fromY, int toX, int toY) {
        Graphics2D graphics = drawing.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(penColor);
            graphics.setStroke(new BasicStroke(
                    penWidth,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            graphics.drawLine(fromX, fromY, toX, toY);
        } finally {
            graphics.dispose();
        }
        canvasPanel.repaint();
    }

    private void clearCanvas() {
        Graphics2D graphics = drawing.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(
                    0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        } finally {
            graphics.dispose();
        }
    }

    private BufferedImage copyDrawing() {
        BufferedImage copy = new BufferedImage(
                CANVAS_WIDTH, CANVAS_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(drawing, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private void savePng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "PNG 이미지 (*.png)", "png"));

        if (chooser.showSaveDialog(this)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase().endsWith(".png")) {
            target = new File(target.getParentFile(),
                    target.getName() + ".png");
        }

        if (target.exists()) {
            int choice = JOptionPane.showConfirmDialog(
                    this, "같은 이름의 파일이 있습니다. 덮어쓸까요?",
                    "PNG 저장", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            if (!ImageIO.write(drawing, "png", target)) {
                throw new IOException("PNG 저장 기능을 사용할 수 없습니다.");
            }
            JOptionPane.showMessageDialog(
                    this, "PNG 파일을 저장했습니다.");
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(
                    this, "이미지 저장에 실패했습니다.",
                    "저장 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    // 그림판 화면과 PNG 저장을 먼저 확인하기 위한 실행 진입점
    public static void main(String[] args) {
        SwingUtilities.invokeLater(
                () -> new DrawingFrame(null, null));
    }
}