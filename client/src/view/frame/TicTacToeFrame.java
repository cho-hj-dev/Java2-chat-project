package view.frame;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class TicTacToeFrame extends JFrame {

    private final JButton[][] cells = new JButton[3][3];
    private final char[][] board = new char[3][3];
    private final JLabel statusLabel = new JLabel(
            "X 차례", SwingConstants.CENTER);

    private char currentPlayer = 'X';
    private int moveCount = 0;
    private boolean gameOver = false;

    public TicTacToeFrame() {
        super("틱택토");

        setSize(420, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        statusLabel.setFont(new Font("맑은 고딕", Font.BOLD, 22));
        add(statusLabel, BorderLayout.NORTH);

        JPanel boardPanel = new JPanel(new GridLayout(3, 3, 5, 5));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                JButton cell = new JButton();
                cell.setFont(new Font("Dialog", Font.BOLD, 64));

                final int selectedRow = row;
                final int selectedCol = col;
                cell.addActionListener(event ->
                        playMove(selectedRow, selectedCol));

                cells[row][col] = cell;
                boardPanel.add(cell);
            }
        }

        add(boardPanel, BorderLayout.CENTER);

        JButton restartButton = new JButton("새 게임");
        restartButton.addActionListener(event -> restartGame());
        add(restartButton, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void playMove(int row, int col) {
        // 게임이 끝났거나 이미 선택한 칸은 다시 누를 수 없다.
        if (gameOver || board[row][col] != '\0') {
            return;
        }

        board[row][col] = currentPlayer;
        cells[row][col].setText(String.valueOf(currentPlayer));
        cells[row][col].setEnabled(false);
        moveCount++;

        if (hasWon(currentPlayer)) {
            gameOver = true;
            statusLabel.setText(currentPlayer + " 승리!");
            disableRemainingCells();
            return;
        }

        if (moveCount == 9) {
            gameOver = true;
            statusLabel.setText("무승부!");
            return;
        }

        // 승패가 결정되지 않았으면 다음 사람에게 차례를 넘긴다.
        currentPlayer = currentPlayer == 'X' ? 'O' : 'X';
        statusLabel.setText(currentPlayer + " 차례");
    }

    private boolean hasWon(char player) {
        // 가로줄과 세로줄을 검사한다.
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == player
                    && board[i][1] == player
                    && board[i][2] == player) {
                return true;
            }

            if (board[0][i] == player
                    && board[1][i] == player
                    && board[2][i] == player) {
                return true;
            }
        }

        // 두 대각선을 검사한다.
        return (board[0][0] == player
                && board[1][1] == player
                && board[2][2] == player)
                || (board[0][2] == player
                && board[1][1] == player
                && board[2][0] == player);
    }

    private void disableRemainingCells() {
        for (JButton[] row : cells) {
            for (JButton cell : row) {
                cell.setEnabled(false);
            }
        }
    }

    private void restartGame() {
        // 게임판과 차례를 처음 상태로 되돌린다.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                board[row][col] = '\0';
                cells[row][col].setText("");
                cells[row][col].setEnabled(true);
            }
        }

        currentPlayer = 'X';
        moveCount = 0;
        gameOver = false;
        statusLabel.setText("X 차례");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TicTacToeFrame::new);
    }
}