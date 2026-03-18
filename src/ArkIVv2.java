/*
 * ArkIVv2 - Enhanced To-Do List with Encryption & Advanced Features
 *
 * New Features Compared to TaskManagerVer1:
 *
 * 1. **Data Encryption**
 *    - Tasks are no longer stored in plain text.
 *    - AES/CBC/PKCS5Padding with PBKDF2 key derivation is used.
 *    - Secret key, salt, and IV are hardcoded for repeatable encryption/decryption.
 *    - Saved file (`data_2.txt`) contains Base64-encoded encrypted data.
 *
 * 2. **Improved Task Layout**
 *    - Uses JTextArea instead of JLabel, enabling multi-line task descriptions.
 *    - Dynamic height with a minimum of 60px ensures tasks never look cramped.
 *    - Cleaner indentation and border styling for subtasks.
 *
 * 3. **Better Scrolling Experience**
 *    - Custom scroll speed (35px per scroll).
 *    - Background of viewport set to light gray for smoother look.
 *
 * 4. **Enhanced Editing**
 *    - Task editing now uses a JTextArea inside a dialog for multiline editing.
 *    - Pressing Enter (without Shift) saves and closes the edit dialog.
 *    - Strikethrough effect handled by Unicode combining characters (̶), not HTML tags.
 *
 * 5. **Improved Deletion Workflow**
 *    - Subtask deletion confirms with a clear message.
 *    - Main task deletion checks for subtasks:
 *         → If present, it deletes both the main task and its subtasks.
 *         → Otherwise, it deletes only the main task.
 *    - Confirmation dialogs now use styled JTextArea for better readability.
 *
 * 6. **Subtask Creation Improvements**
 *    - Subtasks can be entered via a dialog with a JTextArea (supports multiline).
 *    - Subtasks are inserted directly below their parent task.
 *
 * 7. **UI Polish**
 *    - Frame is centered on screen (`setLocationRelativeTo(null)`).
 *    - Font sizes consistent and large for readability.
 *    - Borders are thicker and visually distinct for tasks vs. subtasks.
 *
 * Features Removed/Changed from Ver1:
 * -----------------------------------
 * - Plain text saving/loading (removed → replaced with encrypted storage).
 * - Strikethrough via <html><strike> (removed → replaced with Unicode strike).
 * - JLabel for tasks (removed → replaced with JTextArea for multiline support).
 *
 * Data Saving Format in Ver2:
 * ---------------------------
 * 1. Internally (before encryption):
 *
 *      ID|DONE|TEXT|ISSUBTASK
 *
 *      - **ID** → Unique integer ID of the task.
 *      - **DONE** → "1" if task is marked done, "0" otherwise.
 *      - **TEXT** → The actual task description (raw text).
 *      - **ISSUBTASK** → "true" if subtask, "false" if main task.
 *
 *      Example plain data:
 *          1|0|Buy groceries|false
 *          2|1|Milk|true
 *
 * 2. Externally (after encryption):
 *      - The entire plain text block is encrypted using AES.
 *      - The file `data_2.txt` stores a single long Base64 string.
 *
 * Overall:
 * This version focuses on **security, usability, and polish** over Ver1,
 * making it feel closer to a professional-grade encrypted task manager.
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.*;
import java.security.spec.KeySpec;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Base64;
import java.util.ArrayList;

public class ArkIVv2 {
    private JFrame frame;
    private JPanel taskPanel;
    private JTextField inputField;
    private int taskCounter = 1;
    private final String FILE_NAME = "data_2.txt";

    private static final String SECRET_KEY = "dataEncryptKey15";
    private static final String SALT = "dataEncryptSalt7";
    private static final String IV = "dataEncryptIV328";

    public ArkIVv2() {
        frame = new JFrame("Task Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 800);
        frame.setLayout(new BorderLayout());
        frame.setLocationRelativeTo(null);

        taskPanel = new JPanel();
        taskPanel.setLayout(new BoxLayout(taskPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(taskPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.getVerticalScrollBar().setUnitIncrement(35);
        scrollPane.getViewport().setBackground(new Color(245, 245, 245));
        frame.add(scrollPane, BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        inputField.setPreferredSize(new Dimension(800, 40));

        inputField.addActionListener(e -> addTaskFromInput());

        frame.add(inputField, BorderLayout.SOUTH);

        loadTasks();

        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
        frame.setVisible(true);
        
        /*
        long start = System.currentTimeMillis();
        // existing decryption + task creation code
        long end = System.currentTimeMillis();
        System.out.println("Loaded tasks in " + (end - start) + " ms");
        */

    }

    private void addTaskFromInput() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            TaskItem task = new TaskItem(taskCounter++, text, false, false);
            taskPanel.add(task, 0);
            taskPanel.revalidate();
            inputField.setText("");
            saveTasks();
        }
    }

    private void loadTasks() {
        File file = new File(FILE_NAME);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder encryptedBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    encryptedBuilder.append(line);
                }
                String encryptedData = encryptedBuilder.toString();
                if (!encryptedData.isEmpty()) {
                    String decryptedData = decrypt(encryptedData);
                    BufferedReader dataReader = new BufferedReader(new StringReader(decryptedData));
                    String dataLine;
                    while ((dataLine = dataReader.readLine()) != null) {
                        String[] parts = dataLine.split("\\|", 4);
                        if (parts.length == 4) {
                            int id = Integer.parseInt(parts[0]);
                            boolean done = parts[1].equals("1");
                            String text = parts[2];
                            boolean isSubtask = Boolean.parseBoolean(parts[3]);
                            taskCounter = Math.max(taskCounter, id + 1);
                            TaskItem task = new TaskItem(id, text, done, isSubtask);
                            taskPanel.add(task);
                        }
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Error loading tasks (decryption failed)");
            }
        }
    }

    private void saveTasks() {
        try {
            StringBuilder plainBuilder = new StringBuilder();
            for (Component comp : taskPanel.getComponents()) {
                if (comp instanceof TaskItem) {
                    TaskItem task = (TaskItem) comp;
                    plainBuilder.append(task.getId())
                            .append("|")
                            .append(task.isDone() ? "1" : "0")
                            .append("|")
                            .append(task.getRawText())
                            .append("|")
                            .append(task.isSubtask())
                            .append("\n");
                }
            }
            String plainText = plainBuilder.toString();
            String encryptedText = encrypt(plainText);
            try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_NAME))) {
                writer.print(encryptedText);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error saving tasks (encryption failed)");
        }
    }

    private String encrypt(String plainText) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), SALT.getBytes(), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivspec = new IvParameterSpec(IV.getBytes());
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String decrypt(String encryptedText) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), SALT.getBytes(), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivspec = new IvParameterSpec(IV.getBytes());
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
        byte[] decodedEncrypted = Base64.getMimeDecoder().decode(encryptedText);
        byte[] decrypted = cipher.doFinal(decodedEncrypted);
        return new String(decrypted, "UTF-8");
    }

    private class TaskItem extends JPanel {
        private int id;
        private boolean isSubtask;
        private JCheckBox checkBox;
        private JTextArea textArea;

        public TaskItem(int id, String text, boolean done, boolean isSubtask) {
            this.id = id;
            this.isSubtask = isSubtask;
            setLayout(new BorderLayout());
            setBackground(isSubtask ? new Color(240, 240, 240) : Color.WHITE);
            setBorder(isSubtask ? BorderFactory.createMatteBorder(5, 30, 2, 30, Color.LIGHT_GRAY)
                    : BorderFactory.createMatteBorder(1, 0, 0, 0, Color.DARK_GRAY));
            setOpaque(true);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            checkBox = new JCheckBox();
            checkBox.setPreferredSize(new Dimension(30, 30));
            checkBox.setSelected(done);
            checkBox.addActionListener(e -> toggleDone());

            textArea = new JTextArea(text) {
                @Override
                public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    int minHeight = 60;
                    if (d.height < minHeight) d.height = minHeight;
                    return d;
                }
            };
            textArea.setFont(new Font("Segoe UI", Font.PLAIN, 20));
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setEditable(false);
            textArea.setOpaque(false);
            textArea.setBorder(null);
            if (done) {
                textArea.setForeground(Color.GRAY);
                textArea.setText(text.replaceAll(".", "̶$0"));
            }

            JPanel leftPanel = new JPanel(new BorderLayout());
            leftPanel.setOpaque(false);
            leftPanel.add(checkBox, BorderLayout.WEST);
            leftPanel.add(textArea, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setOpaque(false);

            JButton editButton = new JButton("Edit");
            editButton.addActionListener(e -> editTask());

            JButton deleteButton = new JButton("Delete");
            deleteButton.addActionListener(e -> confirmDeleteTask());

            buttonPanel.add(editButton);
            buttonPanel.add(deleteButton);

            if (!isSubtask) {
                JButton createSubtaskButton = new JButton("Create Subtask");
                createSubtaskButton.addActionListener(e -> createSubtask());
                buttonPanel.add(createSubtaskButton);
            }

            add(leftPanel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.EAST);
        }

        public int getId() { return id; }
        public boolean isDone() { return checkBox.isSelected(); }
        public String getRawText() { return textArea.getText().replaceAll("\\u0336", ""); }
        public boolean isSubtask() { return isSubtask; }

        private void toggleDone() {
            if (checkBox.isSelected()) {
                textArea.setText(getRawText().replaceAll(".", "̶$0"));
                textArea.setForeground(Color.GRAY);
            } else {
                textArea.setText(getRawText());
                textArea.setForeground(Color.BLACK);
            }
            saveTasks();
        }

        private void editTask() {
            JPanel panel = new JPanel(new BorderLayout());
            JTextArea field = new JTextArea(getRawText());
            SwingUtilities.invokeLater(field::selectAll);

            JScrollPane scrollPane = new JScrollPane(field);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            field.setFont(new Font("Segoe UI", Font.PLAIN, 20));
            field.setLineWrap(true);
            field.setWrapStyleWord(true);

            field.setMargin(new Insets(10, 10, 10, 10));
            field.setRows(2);
            field.setPreferredSize(new Dimension(700, 50));

            panel.add(new JLabel("Edit Task:"), BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            field.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                        e.consume();
                        ((JDialog) SwingUtilities.getWindowAncestor(panel)).dispose();
                        String newText = field.getText().trim();
                        if (!newText.isEmpty()) {
                            textArea.setText(checkBox.isSelected() ? newText.replaceAll(".", "̶$0") : newText);
                            saveTasks();
                        }
                    }
                }
            });

            JDialog dialog = new JDialog(frame, "Edit Task", true);
            dialog.getContentPane().add(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);
        }

        private void confirmDeleteTask() {
            if (isSubtask) {
                String taskName = getRawText();
                String message = "This will delete selected subtask ";

                JTextArea messageArea = new JTextArea(message);
                messageArea.setFont(new Font("Segoe UI", Font.PLAIN, 18));
                messageArea.setWrapStyleWord(true);
                messageArea.setLineWrap(true);
                messageArea.setEditable(false);
                messageArea.setOpaque(false);
                messageArea.setPreferredSize(new Dimension(360, 60));


                int choice = JOptionPane.showConfirmDialog(frame, messageArea, "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (choice == JOptionPane.YES_OPTION) {
                    taskPanel.remove(this);
                    taskPanel.revalidate();
                    taskPanel.repaint();
                    saveTasks();
                }
            }
            else {
                java.util.List<Component> toRemove = new ArrayList<>();
                boolean hasSubtasks = false;
                boolean found = false;
                for (Component comp : taskPanel.getComponents()) {
                    if (comp == this) {
                        found = true;
                        toRemove.add(comp);
                    } else if (found && comp instanceof TaskItem && ((TaskItem) comp).isSubtask()) {
                        toRemove.add(comp);
                        hasSubtasks = true;
                    } else if (found) {
                        break;
                    }
                }
                String taskName = getRawText();
                String message = hasSubtasks
                        ? "This will delete \"" + taskName + "\" and all its subtasks"
                        : "This will delete \"" + taskName + "\"?";
                JTextArea messageArea = new JTextArea(message);
                messageArea.setFont(new Font("Segoe UI", Font.PLAIN, 18));
                messageArea.setWrapStyleWord(true);
                messageArea.setLineWrap(true);
                messageArea.setEditable(false);
                messageArea.setOpaque(false);
                messageArea.setPreferredSize(new Dimension(400, 60));

                int choice = JOptionPane.showConfirmDialog(frame, messageArea, "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (choice == JOptionPane.YES_OPTION) {
                    for (Component c : toRemove) {
                        taskPanel.remove(c);
                    }
                }
            }
            taskPanel.revalidate();
            taskPanel.repaint();
            saveTasks();
        }

        private void createSubtask() {
            JPanel panel = new JPanel(new BorderLayout());
            JTextArea field = new JTextArea();
            JScrollPane scrollPane = new JScrollPane(field);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            field.setFont(new Font("Segoe UI", Font.PLAIN, 20));
            field.setLineWrap(true);
            field.setWrapStyleWord(true);

            field.setMargin(new Insets(10, 10, 10, 10));
            field.setRows(2);
            field.setPreferredSize(new Dimension(700, 50));

            panel.add(new JLabel("Enter Subtask:"), BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            field.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                        e.consume();
                        ((JDialog) SwingUtilities.getWindowAncestor(panel)).dispose();
                        String subtaskText = field.getText().trim();
                        if (!subtaskText.isEmpty()) {
                            TaskItem subtask = new TaskItem(taskCounter++, subtaskText, false, true);
                            int i = 0;
                            for (; i < taskPanel.getComponentCount(); i++) {
                                if (taskPanel.getComponent(i) == TaskItem.this) {
                                    break;
                                }
                            }
                            taskPanel.add(subtask, i + 1);
                            taskPanel.revalidate();
                            taskPanel.repaint();
                            saveTasks();
                        }
                    }
                }
            });

            JDialog dialog = new JDialog(frame, "New Subtask", true);
            dialog.getContentPane().add(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ArkIVv2::new);
    }
}


