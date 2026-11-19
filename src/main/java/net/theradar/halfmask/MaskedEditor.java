package net.theradar.halfmask;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;

import java.awt.Component;

/**
 * The "Masked" tab that appears next to Pretty / Raw / Hex in every message editor (Proxy history, Repeater, Logger...).
 * It shows the same message with the rules applied and is read-only. The real message is never changed.
 */
public final class MaskedEditor implements ExtensionProvidedHttpRequestEditor, ExtensionProvidedHttpResponseEditor {
    private final RuleStore store;
    private final RawEditor editor;
    private final boolean isRequest;
    private HttpRequestResponse current;

    public MaskedEditor(MontoyaApi api, RuleStore store, boolean isRequest) {
        this.store = store;
        this.isRequest = isRequest;
        this.editor = api.userInterface().createRawEditor(EditorOptions.READ_ONLY);
        store.addListener(this::refresh);
    }

    private void refresh() {
        if (current == null) return;
        String text = isRequest
                ? (current.request() == null ? "" : current.request().toString())
                : (current.response() == null ? "" : current.response().toString());
        editor.setContents(ByteArray.byteArray(store.masker().mask(text)));
    }

    @Override public HttpRequest getRequest() { return current == null ? null : current.request(); }
    @Override public HttpResponse getResponse() { return current == null ? null : current.response(); }
    @Override public void setRequestResponse(HttpRequestResponse requestResponse) { current = requestResponse; refresh(); }
    @Override public boolean isEnabledFor(HttpRequestResponse rr) {
        if (!store.isEnabled() || rr == null) return false;
        return isRequest ? rr.request() != null : rr.response() != null;
    }
    @Override public String caption() { return "Masked"; }
    @Override public Component uiComponent() { return editor.uiComponent(); }
    @Override public Selection selectedData() { return editor.selection().orElse(null); }
    @Override public boolean isModified() { return false; }
}
