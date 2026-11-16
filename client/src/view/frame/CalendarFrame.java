package view.frame;

import app.Application;
import dto.request.ScheduleRequest;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

public class CalendarFrame extends JFrame {

    private YearMonth visibleMonth = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();
    private LocalDate pendingDate;

    private final List<ScheduleEntry> schedules = new ArrayList<>();
    private final JButton[] dayButtons = new JButton[42];
    private final JLabel monthLabel = new JLabel();
    private final JLabel dayLabel = new JLabel();

    private final DefaultListModel<ScheduleEntry> listModel =
            new DefaultListModel<>();
    private final JList<ScheduleEntry> dayList =
            new JList<>(listModel);

    private final JTextField dateField = new JTextField();
    private final JTextField titleField = new JTextField();
    private final JTextArea detailsArea = new JTextArea();

    public CalendarFrame() {
        super("일정 관리");

        setSize(900, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel navigation = new JPanel(new FlowLayout());
        JButton previous = new JButton("◀ 이전 달");
        JButton next = new JButton("다음 달 ▶");

        previous.addActionListener(event -> changeMonth(-1));
        next.addActionListener(event -> changeMonth(1));

        navigation.add(previous);
        navigation.add(monthLabel);
        navigation.add(next);
        add(navigation, BorderLayout.NORTH);

        JPanel calendarGrid = new JPanel(
                new GridLayout(7, 7, 3, 3));
        String[] weekdays = {
                "일", "월", "화", "수", "목", "금", "토"
        };

        for (String weekday : weekdays) {
            JLabel label = new JLabel(
                    weekday, SwingConstants.CENTER);
            calendarGrid.add(label);
        }

        for (int i = 0; i < dayButtons.length; i++) {
            JButton button = new JButton();
            button.addActionListener(event -> {
                Object value = button.getClientProperty("date");
                if (value instanceof LocalDate date) {
                    selectDate(date);
                }
            });

            dayButtons[i] = button;
            calendarGrid.add(button);
        }

        JPanel dayPanel = new JPanel(new BorderLayout(5, 5));
        dayPanel.add(dayLabel, BorderLayout.NORTH);
        dayPanel.add(new JScrollPane(dayList),
                BorderLayout.CENTER);

        // 목록에서 일정을 선택하면 수정할 값을 입력란에 보여준다.
        dayList.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }

            ScheduleEntry selected = dayList.getSelectedValue();
            if (selected != null) {
                dateField.setText(selected.date().toString());
                titleField.setText(selected.title());
                detailsArea.setText(selected.details());
            }
        });

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                calendarGrid,
                dayPanel);
        split.setResizeWeight(0.65);
        add(split, BorderLayout.CENTER);

        JPanel editor = new JPanel(new BorderLayout(5, 5));
        editor.setPreferredSize(new Dimension(0, 190));

        JPanel fields = new JPanel(new GridLayout(2, 2, 5, 5));
        fields.add(new JLabel("날짜 (yyyy-MM-dd)"));
        fields.add(dateField);
        fields.add(new JLabel("제목"));
        fields.add(titleField);
        editor.add(fields, BorderLayout.NORTH);

        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        editor.add(new JScrollPane(detailsArea),
                BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout());
        JButton createButton = new JButton("등록");
        JButton updateButton = new JButton("수정");
        JButton deleteButton = new JButton("삭제");

        createButton.addActionListener(event -> createSchedule());
        updateButton.addActionListener(event -> updateSchedule());
        deleteButton.addActionListener(event -> deleteSchedule());

        actions.add(createButton);
        actions.add(updateButton);
        actions.add(deleteButton);
        editor.add(actions, BorderLayout.SOUTH);
        add(editor, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (Application.calendarFrame == CalendarFrame.this) {
                    Application.calendarFrame = null;
                }
            }
        });

        dateField.setText(selectedDate.toString());
        renderCalendar();
        renderDayList();
        setVisible(true);
        requestMonth();
    }

    private void changeMonth(int amount) {
        visibleMonth = visibleMonth.plusMonths(amount);
        selectedDate = visibleMonth.atDay(1);
        schedules.clear();

        selectDate(selectedDate);
        requestMonth();
    }

    private void selectDate(LocalDate date) {
        selectedDate = date;
        dayList.clearSelection();
        dateField.setText(date.toString());
        titleField.setText("");
        detailsArea.setText("");

        renderCalendar();
        renderDayList();
    }

    private void renderCalendar() {
        monthLabel.setText(
                visibleMonth.getYear() + "년 "
                + visibleMonth.getMonthValue() + "월");

        LocalDate first = visibleMonth.atDay(1);
        int firstWeekday = first.getDayOfWeek().getValue() % 7;

        for (int i = 0; i < dayButtons.length; i++) {
            JButton button = dayButtons[i];
            int dayNumber = i - firstWeekday + 1;

            if (dayNumber < 1
                    || dayNumber > visibleMonth.lengthOfMonth()) {
                button.setText("");
                button.setEnabled(false);
                button.putClientProperty("date", null);
                continue;
            }

            LocalDate date = visibleMonth.atDay(dayNumber);
            long count = schedules.stream()
                    .filter(item -> item.date().equals(date))
                    .count();

            // 일정이 있는 날에는 건수를 함께 표시한다.
            button.setText("<html><center>" + dayNumber
                    + (count == 0 ? "" : "<br>" + count + "건")
                    + "</center></html>");
            button.setEnabled(true);
            button.putClientProperty("date", date);
            button.setBackground(date.equals(selectedDate)
                    ? new Color(180, 215, 255)
                    : UIManager.getColor("Button.background"));
        }
    }

    private void renderDayList() {
        dayLabel.setText(selectedDate + " 일정");
        listModel.clear();

        for (ScheduleEntry item : schedules) {
            if (item.date().equals(selectedDate)) {
                listModel.addElement(item);
            }
        }
    }

    private void requestMonth() {
        boolean sent = Application.sender.sendMessage(
                ScheduleRequest.list(visibleMonth));

        if (!sent) {
            JOptionPane.showMessageDialog(
                    this, "일정 조회 요청을 보내지 못했습니다.");
        }
    }

    public void showScheduleResult(String message) {
        // 서버 응답: 작업,상태,추가 데이터
        String[] values = message.split(",", 4);
        if (values.length < 2) {
            JOptionPane.showMessageDialog(
                    this, "잘못된 일정 응답입니다.");
            return;
        }

        String action = values[0];
        String status = values[1];

        if (!"OK".equals(status)) {
            pendingDate = null;
            JOptionPane.showMessageDialog(
                    this, "일정 처리 실패: " + status);
            return;
        }

        if ("LIST".equals(action)) {
            showList(values);
            return;
        }

        // 등록·수정·삭제 성공 후 현재 달을 다시 조회한다.
        if (pendingDate != null) {
            visibleMonth = YearMonth.from(pendingDate);
            selectedDate = pendingDate;
            pendingDate = null;
        }

        schedules.clear();
        selectDate(selectedDate);
        requestMonth();
    }

    private void showList(String[] values) {
        if (values.length != 4) {
            JOptionPane.showMessageDialog(
                    this, "잘못된 일정 목록입니다.");
            return;
        }

        try {
            YearMonth responseMonth = YearMonth.parse(values[2]);

            // 이전 달의 응답이 늦게 도착했다면 화면을 덮어쓰지 않는다.
            if (!responseMonth.equals(visibleMonth)) {
                return;
            }

            List<ScheduleEntry> received = new ArrayList<>();

            if (!values[3].isEmpty()) {
                for (String row : values[3].split("\\|")) {
                    String[] fields = row.split(",", 4);
                    if (fields.length != 4) {
                        throw new IllegalArgumentException(
                                "잘못된 일정 항목");
                    }

                    received.add(new ScheduleEntry(
                            Long.parseLong(fields[0]),
                            LocalDate.parse(fields[1]),
                            decode(fields[2]),
                            decode(fields[3])));
                }
            }

            schedules.clear();
            schedules.addAll(received);
            renderCalendar();
            renderDayList();
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(
                    this, "일정 목록을 표시하지 못했습니다.");
        }
    }

    private void createSchedule() {
        LocalDate date = validatedDate();
        if (date == null) {
            return;
        }

        pendingDate = date;
        send(ScheduleRequest.create(
                date,
                titleField.getText().strip(),
                detailsArea.getText()));
    }

    private void updateSchedule() {
        ScheduleEntry selected = dayList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(
                    this, "수정할 일정을 목록에서 선택하세요.");
            return;
        }

        LocalDate date = validatedDate();
        if (date == null) {
            return;
        }

        pendingDate = date;
        send(ScheduleRequest.update(
                selected.id(),
                date,
                titleField.getText().strip(),
                detailsArea.getText()));
    }

    private void deleteSchedule() {
        ScheduleEntry selected = dayList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(
                    this, "삭제할 일정을 목록에서 선택하세요.");
            return;
        }

        int answer = JOptionPane.showConfirmDialog(
                this,
                "선택한 일정을 삭제할까요?",
                "일정 삭제",
                JOptionPane.YES_NO_OPTION);

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        pendingDate = selectedDate;
        send(ScheduleRequest.delete(selected.id()));
    }

    private LocalDate validatedDate() {
        String title = titleField.getText().strip();
        String details = detailsArea.getText();

        if (title.isEmpty()
                || title.length() > 100
                || title.codePoints()
                        .anyMatch(Character::isISOControl)
                || details.length() > 2000
                || details.indexOf('\0') >= 0) {
            JOptionPane.showMessageDialog(
                    this, "제목은 1~100자, 내용은 2000자 이내로 입력하세요.");
            return null;
        }

        try {
            LocalDate date = LocalDate.parse(
                    dateField.getText().trim());

            if (date.getYear() < 1000 || date.getYear() > 9999) {
                throw new IllegalArgumentException("연도 범위");
            }

            return date;
        } catch (DateTimeException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    this, "날짜를 yyyy-MM-dd 형식으로 입력하세요.");
            return null;
        }
    }

    private void send(ScheduleRequest request) {
        if (!Application.sender.sendMessage(request)) {
            pendingDate = null;
            JOptionPane.showMessageDialog(
                    this, "일정 요청을 보내지 못했습니다.");
        }
    }

    private String decode(String encoded) {
        return new String(
                Base64.getDecoder().decode(encoded),
                StandardCharsets.UTF_8);
    }

    private record ScheduleEntry(
            long id,
            LocalDate date,
            String title,
            String details) {

        @Override
        public String toString() {
            return title;
        }
    }
}