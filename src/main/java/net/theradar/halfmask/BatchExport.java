package net.theradar.halfmask;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Unattended export for report builds: when the environment variable HALFMASK_BATCH_DIR is set, every Proxy history
 * item that carries the header named in HALFMASK_BATCH_HEADER is written as a masked PNG, named after the header value.
 * It exists because the right-click export needs an unlocked screen and a person at the Burp window.
 * Nothing is sent to any target; the figures come from the messages Burp already recorded.
 */
final class BatchExport {
    private BatchExport() {}

    static void startIfRequested(MontoyaApi api, RuleStore store) {
        String dir = System.getenv("HALFMASK_BATCH_DIR");
        if (dir == null || dir.isBlank()) return;
        String header = System.getenv("HALFMASK_BATCH_HEADER");
        Thread t = new Thread(() -> run(api, store, new File(dir), header), "halfmask-batch");
        t.setDaemon(true);
        t.start();
    }

    private static void run(MontoyaApi api, RuleStore store, File dir, String header) {
        try {
            Thread.sleep(8000); // let Burp finish loading the project before the history is read
            dir.mkdirs();
            List<ProxyHttpRequestResponse> history = api.proxy().history();
            int written = 0;
            try (PrintWriter index = new PrintWriter(new File(dir, "index.tsv"), StandardCharsets.UTF_8)) {
                index.println("row\ttime\ttag\tmethod\turl\tstatus\tfile");
                for (int i = 0; i < history.size(); i++) {
                    ProxyHttpRequestResponse item = history.get(i);
                    HttpRequest req = item.finalRequest();
                    if (req == null) continue;
                    String tag = header == null || header.isBlank() ? "item" + (i + 1) : req.headerValue(header);
                    if (tag == null) continue;
                    HttpResponse res = item.originalResponse();
                    String name = String.format("%04d_%s.png", i + 1, tag.replaceAll("[^A-Za-z0-9_-]+", "_"));
                    String reqText = figure(store, req.toString());
                    String resText = res == null ? "(no response)" : figure(store, res.toString());
                    String title = req.method() + " " + req.url() + "    Proxy history #" + (i + 1) + ", " + item.time();
                    File out = new File(dir, name);
                    SwingUtilities.invokeAndWait(() -> {
                        try { PngExporter.export(reqText, resText, title, out, true, 40); }
                        catch (Exception e) { api.logging().logToError("HalfMask batch: " + name + ": " + e); }
                    });
                    index.println((i + 1) + "\t" + item.time() + "\t" + tag + "\t" + req.method() + "\t" + req.url() + "\t"
                            + (res == null ? "" : res.statusCode()) + "\t" + name);
                    written++;
                }
            }
            api.logging().logToOutput("HalfMask batch: wrote " + written + " of " + history.size() + " history items to " + dir);
            new File(dir, "DONE").createNewFile();
        } catch (Exception e) {
            api.logging().logToError("HalfMask batch failed: " + e);
        }
    }

    private static String figure(RuleStore store, String raw) {
        return Masker.collapse(store.masker().mask(raw), store.maskChar(), 32);
    }
}
