package net.theradar.halfmask;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Right-click actions: copy the masked message, or export the masked pair as a PNG figure. */
public final class MaskMenu implements ContextMenuItemsProvider {
    private final MontoyaApi api;
    private final RuleStore store;
    private File lastDir;

    public MaskMenu(MontoyaApi api, RuleStore store) { this.api = api; this.store = store; }

    @Override public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<HttpRequestResponse> items = new ArrayList<>();
        event.messageEditorRequestResponse().ifPresent(m -> items.add(m.requestResponse()));
        if (items.isEmpty()) items.addAll(event.selectedRequestResponses());
        if (items.isEmpty()) return List.of();
        List<Component> menu = new ArrayList<>();
        JMenuItem png = new JMenuItem(items.size() == 1 ? "HalfMask: export masked PNG" : "HalfMask: export " + items.size() + " masked PNGs");
        png.addActionListener(e -> exportPng(items));
        menu.add(png);
        if (items.size() == 1) {
            JMenuItem copyReq = new JMenuItem("HalfMask: copy masked request");
            copyReq.addActionListener(e -> copy(figureText(items.get(0).request().toString())));
            menu.add(copyReq);
            if (items.get(0).response() != null) {
                JMenuItem copyRes = new JMenuItem("HalfMask: copy masked response");
                copyRes.addActionListener(e -> copy(figureText(items.get(0).response().toString())));
                menu.add(copyRes);
            }
        }
        return menu;
    }

    /** Masked text for figures and the clipboard: long masked runs are shortened so a token does not fill the page. */
    private String figureText(String raw) {
        return Masker.collapse(store.masker().mask(raw), store.maskChar(), 32);
    }

    private void copy(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }

    private void exportPng(List<HttpRequestResponse> items) {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setDialogTitle(items.size() == 1 ? "Save masked PNG" : "Choose a folder for the masked PNGs");
        fc.setFileSelectionMode(items.size() == 1 ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
        if (items.size() == 1) fc.setSelectedFile(new File(lastDir, suggestName(items.get(0))));
        if (fc.showSaveDialog(api.userInterface().swingUtils().suiteFrame()) != JFileChooser.APPROVE_OPTION) return;
        File target = fc.getSelectedFile();
        lastDir = target.isDirectory() ? target : target.getParentFile();
        int n = 0;
        try {
            for (HttpRequestResponse rr : items) {
                File out = items.size() == 1 ? target : new File(target, suggestName(rr));
                String req = rr.request() == null ? "" : figureText(rr.request().toString());
                String res = rr.response() == null ? "(no response)" : figureText(rr.response().toString());
                String title = rr.request() == null ? "" : rr.request().method() + " " + rr.request().path();
                PngExporter.export(req, res, title, out);
                n++;
            }
            api.logging().logToOutput("HalfMask: wrote " + n + " PNG file(s) to " + lastDir);
        } catch (Exception ex) {
            api.logging().logToError("HalfMask export failed: " + ex);
            JOptionPane.showMessageDialog(api.userInterface().swingUtils().suiteFrame(), "Export failed: " + ex.getMessage(), "HalfMask", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String suggestName(HttpRequestResponse rr) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String path = rr.request() == null ? "message" : rr.request().path().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (path.length() > 40) path = path.substring(0, 40);
        return "halfmask_" + (rr.request() == null ? "" : rr.request().method() + "_") + path + "_" + stamp + ".png";
    }
}
