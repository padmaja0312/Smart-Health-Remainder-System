import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    static final String DATA_FILE = "health_data.ser";

    static HealthData data = loadData();

    static JFrame frame;
    static JTextArea historyArea;
    static JTextArea summaryArea;
    static JTextArea medicationArea;

    static ScheduledExecutorService reminderService =
            Executors.newSingleThreadScheduledExecutor();

    static DateTimeFormatter timeFormat =
            DateTimeFormatter.ofPattern("HH:mm");

    // ================= DATA CLASSES =================

    static class HealthData implements Serializable {
        UserProfile profile = new UserProfile();
        List<Medication> medications = new ArrayList<>();
        List<HealthEntry> healthEntries = new ArrayList<>();
    }

    static class UserProfile implements Serializable {
        String name = "";
        int age;
        String gender = "";
    }

    static class Medication implements Serializable {
        String name;
        String dosage;
        String time;

        Map<String, String> status = new HashMap<>();
        String lastReminder = "";

        Medication(String name, String dosage, String time) {
            this.name = name;
            this.dosage = dosage;
            this.time = time;
        }
    }

    static class HealthEntry implements Serializable {
        String date;
        double weight;
        String bloodPressure;

        HealthEntry(double weight, String bloodPressure) {
            this.date = LocalDate.now().toString();
            this.weight = weight;
            this.bloodPressure = bloodPressure;
        }
    }

    // ================= FILE HANDLING =================

    static HealthData loadData() {
        try (ObjectInputStream in =
                     new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            return (HealthData) in.readObject();
        } catch (Exception e) {
            return new HealthData();
        }
    }

    static void saveData() {
        try (ObjectOutputStream out =
                     new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            out.writeObject(data);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                    "Unable to save data: " + e.getMessage());
        }
    }

    // ================= MAIN =================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createGUI();
            startReminderService();
        });
    }

    // ================= GUI =================

    static void createGUI() {
        frame = new JFrame("PS-08 Smart Personal Health System");

        frame.setSize(850, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", createDashboardPanel());
        tabs.addTab("User Profile", createProfilePanel());
        tabs.addTab("Medication", createMedicationPanel());
        tabs.addTab("Health Tracking", createHealthPanel());
        tabs.addTab("History", createHistoryPanel());
        tabs.addTab("Reports", createReportPanel());

        frame.add(tabs);
        frame.setVisible(true);
    }

    // ================= USER PROFILE =================

    static JPanel createProfilePanel() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        JTextField nameField = new JTextField(data.profile.name);
        JTextField ageField = new JTextField(
                data.profile.age == 0 ? "" :
                        String.valueOf(data.profile.age));
        JTextField genderField = new JTextField(data.profile.gender);

        JButton saveButton = new JButton("Register / Update Profile");

        panel.add(new JLabel("User Name:"));
        panel.add(nameField);

        panel.add(new JLabel("Age:"));
        panel.add(ageField);

        panel.add(new JLabel("Gender:"));
        panel.add(genderField);

        panel.add(new JLabel(""));
        panel.add(saveButton);

        saveButton.addActionListener(e -> {
            try {
                data.profile.name = nameField.getText().trim();
                data.profile.age = Integer.parseInt(ageField.getText().trim());
                data.profile.gender = genderField.getText().trim();

                if (data.profile.name.isEmpty()
                        || data.profile.age <= 0
                        || data.profile.gender.isEmpty()) {
                    throw new IllegalArgumentException();
                }

                saveData();

                JOptionPane.showMessageDialog(frame,
                        "Profile registered successfully!");

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame,
                        "Enter valid profile details.");
            }
        });

        return panel;
    }

    // ================= MEDICATION =================

    static JPanel createMedicationPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(4, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JTextField nameField = new JTextField();
        JTextField dosageField = new JTextField();
        JTextField timeField = new JTextField("09:00");

        JButton addButton = new JButton("Add Medication");
        JButton takenButton = new JButton("Mark Selected as Taken");
        JButton refreshButton = new JButton("Refresh");

        form.add(new JLabel("Medication Name:"));
        form.add(nameField);

        form.add(new JLabel("Dosage:"));
        form.add(dosageField);

        form.add(new JLabel("Schedule (HH:mm):"));
        form.add(timeField);

        form.add(new JLabel(""));
        form.add(addButton);

        medicationArea = new JTextArea();
        medicationArea.setEditable(false);

        JScrollPane scroll = new JScrollPane(medicationArea);

        JPanel buttons = new JPanel();
        buttons.add(takenButton);
        buttons.add(refreshButton);

        panel.add(form, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        addButton.addActionListener(e -> {
            try {
                String name = nameField.getText().trim();
                String dosage = dosageField.getText().trim();
                String time = timeField.getText().trim();

                LocalTime.parse(time, timeFormat);

                if (name.isEmpty() || dosage.isEmpty()) {
                    throw new IllegalArgumentException();
                }

                data.medications.add(
                        new Medication(name, dosage, time));

                saveData();
                refreshMedicationList();

                nameField.setText("");
                dosageField.setText("");

                JOptionPane.showMessageDialog(frame,
                        "Medication added successfully!");

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame,
                        "Enter valid medication details.\n"
                                + "Time format: HH:mm (Example: 09:30)");
            }
        });

        takenButton.addActionListener(e -> {
            String selected = JOptionPane.showInputDialog(frame,
                    "Enter the medication number to mark as taken:");

            try {
                int index = Integer.parseInt(selected) - 1;

                if (index < 0 || index >= data.medications.size()) {
                    throw new IllegalArgumentException();
                }

                Medication med = data.medications.get(index);

                med.status.put(LocalDate.now().toString(), "Taken");

                saveData();
                refreshMedicationList();

                JOptionPane.showMessageDialog(frame,
                        med.name + " marked as Taken.");

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame,
                        "Enter a valid medication number.");
            }
        });

        refreshButton.addActionListener(e -> refreshMedicationList());

        refreshMedicationList();

        return panel;
    }

    static void refreshMedicationList() {
        if (medicationArea == null) return;

        StringBuilder sb = new StringBuilder();

        sb.append("TODAY'S MEDICATIONS\n\n");

        int i = 1;

        for (Medication med : data.medications) {
            String status = med.status.getOrDefault(
                    LocalDate.now().toString(), "Pending");

            sb.append(i++).append(". ")
                    .append(med.name)
                    .append("\nDosage: ").append(med.dosage)
                    .append("\nTime: ").append(med.time)
                    .append("\nStatus: ").append(status)
                    .append("\n---------------------------\n");
        }

        if (data.medications.isEmpty()) {
            sb.append("No medications added.");
        }

        medicationArea.setText(sb.toString());
    }

    // ================= HEALTH TRACKING =================

    static JPanel createHealthPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        JTextField weightField = new JTextField();
        JTextField bpField = new JTextField();

        JButton saveButton = new JButton("Save Health Record");

        panel.add(new JLabel("Weight (kg):"));
        panel.add(weightField);

        panel.add(new JLabel("Blood Pressure (Example: 120/80):"));
        panel.add(bpField);

        panel.add(new JLabel("Date:"));
        panel.add(new JLabel(LocalDate.now().toString()));

        panel.add(new JLabel(""));
        panel.add(saveButton);

        saveButton.addActionListener(e -> {
            try {
                double weight = Double.parseDouble(
                        weightField.getText().trim());

                String bp = bpField.getText().trim();

                if (weight <= 0 || bp.isEmpty()) {
                    throw new IllegalArgumentException();
                }

                data.healthEntries.add(new HealthEntry(weight, bp));

                saveData();

                JOptionPane.showMessageDialog(frame,
                        "Health record saved successfully!");

                weightField.setText("");
                bpField.setText("");

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame,
                        "Enter valid weight and blood pressure.");
            }
        });

        return panel;
    }

    // ================= HISTORY =================

    static JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        historyArea = new JTextArea();
        historyArea.setEditable(false);

        JButton refreshButton = new JButton("Refresh History");

        refreshButton.addActionListener(e -> refreshHistory());

        panel.add(new JScrollPane(historyArea), BorderLayout.CENTER);
        panel.add(refreshButton, BorderLayout.SOUTH);

        refreshHistory();

        return panel;
    }

    static void refreshHistory() {
        if (historyArea == null) return;

        StringBuilder sb = new StringBuilder();

        sb.append("===== MEDICATION HISTORY =====\n\n");

        for (Medication med : data.medications) {
            sb.append("Medication: ").append(med.name)
                    .append("\nDosage: ").append(med.dosage)
                    .append("\nSchedule: ").append(med.time)
                    .append("\n");

            for (Map.Entry<String, String> entry
                    : med.status.entrySet()) {
                sb.append(entry.getKey())
                        .append(" : ")
                        .append(entry.getValue())
                        .append("\n");
            }

            sb.append("----------------------------\n");
        }

        sb.append("\n===== HEALTH RECORD HISTORY =====\n\n");

        for (HealthEntry entry : data.healthEntries) {
            sb.append("Date: ").append(entry.date)
                    .append("\nWeight: ").append(entry.weight)
                    .append(" kg\nBlood Pressure: ")
                    .append(entry.bloodPressure)
                    .append("\n----------------------------\n");
        }

        historyArea.setText(sb.toString());
    }

    // ================= REPORTS =================

    static JPanel createReportPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        summaryArea = new JTextArea();
        summaryArea.setEditable(false);

        JButton dailyButton = new JButton("Daily Summary");
        JButton weeklyButton = new JButton("Weekly Summary");

        JPanel buttons = new JPanel();

        buttons.add(dailyButton);
        buttons.add(weeklyButton);

        dailyButton.addActionListener(e -> generateDailySummary());
        weeklyButton.addActionListener(e -> generateWeeklySummary());

        panel.add(new JScrollPane(summaryArea), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    static void generateDailySummary() {
        String today = LocalDate.now().toString();

        StringBuilder sb = new StringBuilder();

        sb.append("===== DAILY HEALTH SUMMARY =====\n");
        sb.append("Date: ").append(today).append("\n\n");

        int taken = 0;
        int pending = 0;

        for (Medication med : data.medications) {
            String status = med.status.getOrDefault(today, "Pending");

            sb.append(med.name)
                    .append(" - ")
                    .append(status)
                    .append("\n");

            if (status.equals("Taken")) {
                taken++;
            } else {
                pending++;
            }
        }

        sb.append("\nTotal Medications: ")
                .append(data.medications.size());

        sb.append("\nTaken: ").append(taken);
        sb.append("\nPending: ").append(pending);

        sb.append("\n\n===== TODAY'S HEALTH RECORDS =====\n");

        for (HealthEntry entry : data.healthEntries) {
            if (entry.date.equals(today)) {
                sb.append("Weight: ")
                        .append(entry.weight)
                        .append(" kg\nBlood Pressure: ")
                        .append(entry.bloodPressure)
                        .append("\n");
            }
        }

        summaryArea.setText(sb.toString());
    }

    static void generateWeeklySummary() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);

        StringBuilder sb = new StringBuilder();

        sb.append("===== WEEKLY HEALTH SUMMARY =====\n");
        sb.append("From: ").append(start)
                .append("\nTo: ").append(today)
                .append("\n\n");

        int taken = 0;
        int pending = 0;

        for (Medication med : data.medications) {
            sb.append("\nMedication: ")
                    .append(med.name)
                    .append("\n");

            for (int i = 0; i < 7; i++) {
                LocalDate date = start.plusDays(i);

                String status = med.status.getOrDefault(
                        date.toString(), "Pending");

                sb.append(date)
                        .append(" : ")
                        .append(status)
                        .append("\n");

                if (status.equals("Taken")) {
                    taken++;
                } else {
                    pending++;
                }
            }
        }

        sb.append("\n===== WEEKLY TOTAL =====\n");
        sb.append("Taken: ").append(taken);
        sb.append("\nPending: ").append(pending);

        sb.append("\n\n===== HEALTH PARAMETERS =====\n");

        for (HealthEntry entry : data.healthEntries) {
            LocalDate date = LocalDate.parse(entry.date);

            if (!date.isBefore(start) && !date.isAfter(today)) {
                sb.append(entry.date)
                        .append(" | Weight: ")
                        .append(entry.weight)
                        .append(" kg | BP: ")
                        .append(entry.bloodPressure)
                        .append("\n");
            }
        }

        summaryArea.setText(sb.toString());
    }

    // ================= REMINDERS =================

    static void startReminderService() {
        reminderService.scheduleAtFixedRate(() -> {
            LocalTime now = LocalTime.now();

            String today = LocalDate.now().toString();
            String currentTime = now.format(timeFormat);

            for (Medication med : data.medications) {
                String status = med.status.getOrDefault(
                        today, "Pending");

                if (med.time.equals(currentTime)
                        && !med.lastReminder.equals(today + " " + currentTime)
                        && !status.equals("Taken")) {

                    med.lastReminder = today + " " + currentTime;

                    saveData();

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(frame,
                                "Medication Reminder!\n\n"
                                        + "Medicine: " + med.name
                                        + "\nDosage: " + med.dosage
                                        + "\nScheduled Time: " + med.time
                                        + "\n\nPlease check your medication plan.",
                                "Medication Reminder",
                                JOptionPane.INFORMATION_MESSAGE);
                    });
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

static JPanel createDashboardPanel() {

    JPanel dashboard = new JPanel(new BorderLayout(15, 15));

    dashboard.setBorder(
        BorderFactory.createEmptyBorder(20, 20, 20, 20)
    );

    // Welcome message
    String userName = data.profile.name;

    if (userName == null || userName.trim().isEmpty()) {
        userName = "User";
    }

    JLabel welcome = new JLabel(
        "Welcome, " + userName + "!"
    );

    welcome.setFont(new Font("Arial", Font.BOLD, 24));

    dashboard.add(welcome, BorderLayout.NORTH);

    // Calculate total records
    int totalMedications = data.medications.size();

    int totalHealthRecords = data.healthEntries.size();

    // Medication summary
    String medicationInfo =
        "Total Medicines: " + totalMedications;

    // Reminder summary
    StringBuilder reminderInfo = new StringBuilder();

    if (totalMedications == 0) {
        reminderInfo.append("No medicines added yet.");
    } else {
        for (int i = 0; i < totalMedications && i < 4; i++) {

            Medication med = data.medications.get(i);

            reminderInfo.append(med.name)
                .append(" - ")
                .append(med.time)
                .append("<br>");
        }
    }

    // Health summary
    String healthInfo;

    if (totalHealthRecords == 0) {

        healthInfo = "No health records available.";

    } else {

        HealthEntry latest =
            data.healthEntries.get(totalHealthRecords - 1);

        healthInfo =
            "Latest Weight: " + latest.weight + " kg<br>" +
            "Blood Pressure: " + latest.bloodPressure;
    }

    // Reports summary
    String reportInfo =
        "Total Health Records: " + totalHealthRecords;

    // Create dashboard cards
    JPanel cards = new JPanel(
        new GridLayout(2, 2, 15, 15)
    );

    cards.add(createDashboardCard(
        "MEDICATIONS",
        medicationInfo
    ));

    cards.add(createDashboardCard(
        "MEDICATION SCHEDULE",
        reminderInfo.toString()
    ));

    cards.add(createDashboardCard(
        "HEALTH TRACKING",
        healthInfo
    ));

    cards.add(createDashboardCard(
        "HEALTH REPORTS",
        reportInfo
    ));

    dashboard.add(cards, BorderLayout.CENTER);

    JLabel footer = new JLabel(
        "Stay Healthy | Track Your Health Daily",
        JLabel.CENTER
    );

    dashboard.add(footer, BorderLayout.SOUTH);

    return dashboard;
}


// Dashboard Card Method
static JPanel createDashboardCard(
        String heading,
        String description) {

    JPanel card = new JPanel(new BorderLayout(10, 10));

    card.setBorder(
        BorderFactory.createTitledBorder(heading)
    );

    JLabel content = new JLabel(
        "<html><center>" + description +
        "</center></html>",
        JLabel.CENTER
    );

    content.setFont(new Font("Arial", Font.PLAIN, 16));

    card.add(content, BorderLayout.CENTER);

    return card;
}
}