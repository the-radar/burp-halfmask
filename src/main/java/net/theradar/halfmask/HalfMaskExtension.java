package net.theradar.halfmask;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

/**
 * HalfMask: partial masking of secrets and personal data for report screenshots.
 * Adds a read-only "Masked" tab to every request and response editor, a HalfMask settings tab, and right-click export of
 * masked request/response pairs as PNG figures. Traffic, history and project files are never modified.
 */
public final class HalfMaskExtension implements BurpExtension {
    @Override public void initialize(MontoyaApi api) {
        api.extension().setName("HalfMask");
        RuleStore store = new RuleStore(api.persistence().preferences());
        api.userInterface().registerSuiteTab("HalfMask", new SettingsPanel(store));
        api.userInterface().registerHttpRequestEditorProvider(ctx -> new MaskedEditor(api, store, true));
        api.userInterface().registerHttpResponseEditorProvider(ctx -> new MaskedEditor(api, store, false));
        api.userInterface().registerContextMenuItemsProvider(new MaskMenu(api, store));
        // The only background work is the optional one-shot batch export, which ends by itself.
        api.extension().registerUnloadingHandler(store::clearListeners);
        api.logging().logToOutput("HalfMask loaded: " + store.rules().size() + " rules. Open the Masked tab on any message, or right-click to export a masked PNG.");
    }
}
