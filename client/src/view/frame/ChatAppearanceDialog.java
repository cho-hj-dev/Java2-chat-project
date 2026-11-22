package view.frame;

import app.Application;
import view.panel.ChatPanel;
import view.style.ChatAppearance;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class ChatAppearanceDialog extends JDialog {

    private final JComboBox<String> fontBox = new JComboBox<>();
    private final JSpinner sizeSpinner;
    private final JButton colorButton = new JButton("배경색 선택");

    private Color selectedColor;

    public ChatAppearanceDialog(JFrame owner) {
        super(owner, "채팅 화면 설정", true);

        ChatAppearance current = ChatAppearance.load();
        selectedColor = current.backgroundColor();

        setLayout(new GridLayout(4, 1, 8, 8));
        setSize(420, 260);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel colorRow = new JPanel();
        colorRow.add(new JLabel("배경색"));
        colorButton.setBackground(selectedColor);
        colorButton.addActionListener(event -> chooseColor());
        colorRow.add(colorButton);
        add(colorRow);

        // Java 기본 글꼴과 PC에 설치된 글꼴을 선택지로 제공한다.
        fontBox.addItem(Font.DIALOG);
        for (String family : GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            if (!Font.DIALOG.equals(family)) {
                fontBox.addItem(family);
            }
        }
        fontBox.setSelectedItem(current.fontFamily());

        JPanel fontRow = new JPanel();
        fontRow.add(new JLabel("글꼴"));
        fontBox.setPrototypeDisplayValue("가나다라마바사 ABCDEFG");
        fontRow.add(fontBox);
        add(fontRow);

        sizeSpinner = new JSpinner(new SpinnerNumberModel(
                current.fontSize(), 10, 36, 1));

        JPanel sizeRow = new JPanel();
        sizeRow.add(new JLabel("글자 크기"));
        sizeRow.add(sizeSpinner);
        add(sizeRow);

        JButton saveButton = new JButton("적용");
        saveButton.addActionListener(event -> saveAppearance());

        JButton cancelButton = new JButton("취소");
        cancelButton.addActionListener(event -> dispose());

        JPanel buttonRow = new JPanel();
        buttonRow.add(saveButton);
        buttonRow.add(cancelButton);
        add(buttonRow);

        setLocationRelativeTo(owner);
        setVisible(true);
    }

    private void chooseColor() {
        Color chosen = JColorChooser.showDialog(
                this, "채팅 배경색 선택", selectedColor);

        if (chosen != null) {
            selectedColor = chosen;
            colorButton.setBackground(chosen);
        }
    }

    private void saveAppearance() {
        String fontFamily = (String) fontBox.getSelectedItem();
        int fontSize = (Integer) sizeSpinner.getValue();

        ChatAppearance appearance = new ChatAppearance(
                selectedColor, fontFamily, fontSize);

        if (!appearance.save()) {
            JOptionPane.showMessageDialog(
                    this, "화면 설정을 저장하지 못했습니다.");
            return;
        }

        // 이미 열려 있는 로비와 채팅방에도 변경 사항을 즉시 적용한다.
        for (ChatPanel panel : Application.chatPanelMap.values()) {
            panel.applyAppearance(appearance);
        }

        dispose();
    }
}