package net.theradar.halfmask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/** The HalfMask suite tab: the rule table, a live test box, and the global switch. */
public final class SettingsPanel extends JPanel {
    private static final String[] COLS = {"On", "Name", "Kind", "Header / key / param / regex", "Visible chars (-1 = half)"};
    private final RuleStore store;
    private final DefaultTableModel model;
    private final JTable table;
    private final JTextArea sample = new JTextArea(8, 60);
    private final JTextArea preview = new JTextArea(8, 60);
    private boolean loading;

    public SettingsPanel(RuleStore store) {
        super(new BorderLayout(8, 8));
        this.store = store;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JCheckBox on = new JCheckBox("Show the Masked tab in message editors", store.isEnabled());
        on.addActionListener(e -> store.setEnabled(on.isSelected()));
        top.add(on);
        top.add(new JLabel("Values keep their length. Rules run top to bottom; the first rule to cover a character wins."));
        add(top, BorderLayout.NORTH);

        model = new DefaultTableModel(COLS, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : c == 4 ? Integer.class : String.class; }
        };
        table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(2).setMaxWidth(110);
        table.getColumnModel().getColumn(4).setMaxWidth(170);
        JComboBox<String> kinds = new JComboBox<>();
        for (MaskRule.Kind k : MaskRule.Kind.values()) kinds.addItem(k.name());
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(kinds));
        model.addTableModelListener(e -> { if (!loading) pushToStore(); });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.add(button("Add", () -> { model.addRow(new Object[]{true, "New rule", "JSON_FIELD", "", -1}); }));
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(button("Remove", () -> { int[] rows = table.getSelectedRows(); for (int i = rows.length - 1; i >= 0; i--) model.removeRow(rows[i]); pushToStore(); }));
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(button("Up", () -> move(-1)));
        buttons.add(Box.createVerticalStrut(4));
        buttons.add(button("Down", () -> move(1)));
        buttons.add(Box.createVerticalStrut(12));
        buttons.add(button("Defaults", () -> { store.resetToDefaults(); loadFromStore(); }));

        JPanel rules = new JPanel(new BorderLayout(6, 0));
        rules.add(new JScrollPane(table), BorderLayout.CENTER);
        rules.add(buttons, BorderLayout.EAST);

        sample.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        preview.setEditable(false);
        sample.setText("POST /api/login HTTP/1.1\r\nHost: example.test\r\nAuthorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqYW5lIn0.abcdefghijklmnopqrstuvwxyz0123456789\r\nCookie: session=9f8e7d6c5b4a39281716; theme=dark\r\n\r\n{\"username\":\"jane.doe@example.test\",\"password\":\"Winter2026!\",\"otp\":\"123456\"}");
        sample.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
        });
        JPanel test = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        test.add(titled("Paste a message to test", new JScrollPane(sample)));
        test.add(titled("Masked result", new JScrollPane(preview)));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rules, test);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        store.addListener(this::updatePreview);
        loadFromStore();
    }

    private static JPanel titled(String t, java.awt.Component c) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.add(new JLabel(t), BorderLayout.NORTH); p.add(c, BorderLayout.CENTER); return p;
    }

    private JButton button(String label, Runnable r) {
        JButton b = new JButton(label); b.setAlignmentX(LEFT_ALIGNMENT); b.addActionListener(e -> r.run()); return b;
    }

    private void move(int delta) {
        int row = table.getSelectedRow();
        int to = row + delta;
        if (row < 0 || to < 0 || to >= model.getRowCount()) return;
        model.moveRow(row, row, to);
        table.setRowSelectionInterval(to, to);
        pushToStore();
    }

    private void loadFromStore() {
        loading = true;
        model.setRowCount(0);
        for (MaskRule r : store.rules()) model.addRow(new Object[]{r.enabled, r.name, r.kind.name(), r.pattern, r.visible});
        loading = false;
        updatePreview();
    }

    private void pushToStore() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        List<MaskRule> out = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            Object vis = model.getValueAt(i, 4);
            int v = vis instanceof Integer ? (Integer) vis : -1;
            MaskRule.Kind k;
            try { k = MaskRule.Kind.valueOf(String.valueOf(model.getValueAt(i, 2))); } catch (RuntimeException e) { k = MaskRule.Kind.REGEX; }
            out.add(new MaskRule(Boolean.TRUE.equals(model.getValueAt(i, 0)), String.valueOf(model.getValueAt(i, 1)), k, String.valueOf(model.getValueAt(i, 3)), v));
        }
        store.replaceRules(out);
    }

    private void updatePreview() {
        javax.swing.SwingUtilities.invokeLater(() -> preview.setText(store.masker().mask(sample.getText())));
    }
}
