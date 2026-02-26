package com.gatopeich.urlvinegar.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gatopeich.urlvinegar.R;
import com.gatopeich.urlvinegar.data.ConfigRepository;
import com.gatopeich.urlvinegar.data.Transform;
import com.gatopeich.urlvinegar.util.UrlProcessor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Processing Activity - Handles URL cleaning dialog.
 * Shows a unified list of query parameters: kept ones with values,
 * removed ones with the transform name that removed them.
 */
public class ProcessingActivity extends AppCompatActivity {

    private ConfigRepository configRepository;
    private List<Transform> transforms;
    private List<UrlProcessor.QueryParam> queryParams;

    private String originalUrl;
    private String currentUrl;
    private String urlHost;
    private boolean isProcessTextIntent;
    private Set<String> userRemovedParams; // Track params the user explicitly unchecked
    private Set<String> userRestoredParams; // Track params the user explicitly re-checked (override transform removal)

    private TextView urlPreview;
    private RecyclerView paramsRecyclerView;
    private LinearLayout paramsSection;
    private QueryParamAdapter paramAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        configRepository = ConfigRepository.getInstance(this);
        transforms = configRepository.loadTransforms();
        userRemovedParams = new HashSet<>();
        userRestoredParams = new HashSet<>();

        // Extract URL from intent
        isProcessTextIntent = Intent.ACTION_PROCESS_TEXT.equals(getIntent().getAction());
        originalUrl = extractUrlFromIntent(getIntent());
        if (originalUrl == null) {
            // For PROCESS_TEXT with no URL, silently finish
            if (isProcessTextIntent) {
                finish();
                return;
            }
            // No URL found, open config activity instead
            startActivity(new Intent(this, ConfigActivity.class));
            finish();
            return;
        }
        
        // Extract host for display in default transform names
        try {
            Uri uri = Uri.parse(originalUrl);
            urlHost = uri.getHost();
            if (urlHost != null && urlHost.startsWith("www.")) {
                urlHost = urlHost.substring(4);
            }
        } catch (Exception e) {
            urlHost = "URL";
        }

        setupViews();
        processUrl();
    }

    /**
     * Requirement 2.2: URL Reception
     */
    private String extractUrlFromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }

        String action = intent.getAction();
        
        // ACTION_VIEW - URL clicked as browser
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                return intent.getData().toString();
            }
        }
        
        // ACTION_SEND - Shared from another app
        if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            return UrlProcessor.extractUrl(text);
        }

        // ACTION_PROCESS_TEXT - Text selected in another app (API 23+)
        // Only process if the selection starts with "http" (case-insensitive)
        if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (text != null && UrlProcessor.looksLikeUrl(text.toString())) {
                return UrlProcessor.extractUrl(text.toString());
            }
        }
        
        return null;
    }

    private void setupViews() {
        urlPreview = findViewById(R.id.urlPreview);
        paramsRecyclerView = findViewById(R.id.paramsRecyclerView);
        paramsSection = findViewById(R.id.paramsSection);

        // Setup query params RecyclerView
        paramAdapter = new QueryParamAdapter();
        paramsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        paramsRecyclerView.setAdapter(paramAdapter);

        // Setup buttons
        // When launched via PROCESS_TEXT, the main button returns the cleaned URL
        // to the calling app; otherwise it opens the share chooser.
        Button mainButton = findViewById(R.id.shareButton);
        if (isProcessTextIntent) {
            mainButton.setText(R.string.apply);
            mainButton.setOnClickListener(v -> returnProcessedText());
        } else {
            mainButton.setOnClickListener(v -> shareUrl());
        }
        findViewById(R.id.copyButton).setOnClickListener(v -> copyUrl());
        findViewById(R.id.cancelButton).setOnClickListener(v -> finish());
        findViewById(R.id.settingsButton).setOnClickListener(v -> openSettings());
    }

    private void processUrl() {
        // Parse params from original URL, track which transform removed each
        queryParams = UrlProcessor.parseParamsWithTracking(
            originalUrl, transforms, null, userRemovedParams);

        // Apply user overrides: if user restored a param that was removed by transform, mark as keep
        for (UrlProcessor.QueryParam p : queryParams) {
            if (userRestoredParams.contains(p.name) && p.removedBy != null) {
                p.keep = true;
            }
        }

        // Reconstruct URL from original base + kept params only.
        // We use originalUrl (not transform output) as the base to avoid
        // params getting duplicated when transforms remove the '?' separator.
        currentUrl = UrlProcessor.reconstructUrl(originalUrl, queryParams);
        
        // Update UI
        urlPreview.setText(currentUrl);
        urlPreview.setTextColor(ContextCompat.getColor(this, R.color.text_dark));
        findViewById(R.id.shareButton).setEnabled(true);
        updateSectionVisibility();
        paramAdapter.notifyDataSetChanged();
    }

    /**
     * Show/hide sections based on content availability.
     */
    private void updateSectionVisibility() {
        // Show params section only if there are query params
        if (queryParams == null || queryParams.isEmpty()) {
            paramsSection.setVisibility(View.GONE);
        } else {
            paramsSection.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Return the cleaned URL to the calling app via PROCESS_TEXT result.
     */
    private void returnProcessedText() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, currentUrl);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    /**
     * Requirement 6.1: Share Intent
     */
    private void shareUrl() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, currentUrl);
        
        // Requirement 6.1: Display system chooser
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
        
        // Requirement 6.2: Finish after sharing
        finish();
    }

    private void copyUrl() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("URL", currentUrl);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    /**
     * Requirement 3.8: Access to full configuration activity
     */
    private void openSettings() {
        startActivity(new Intent(this, ConfigActivity.class));
    }

    /**
     * Requirement 3.5: Adding Transforms
     * Shows a dialog to add a new transform with live preview of the result.
     */
    private void showAddTransformDialog() {
        showAddTransformDialogWithDefaults("", "", "");
    }

    /**
     * Shows transform dialog with pre-filled values.
     */
    private void showAddTransformDialogWithDefaults(String defaultName, String defaultPattern, String defaultReplacement) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transform, null);
        EditText nameEdit = dialogView.findViewById(R.id.transformName);
        EditText patternEdit = dialogView.findViewById(R.id.transformPattern);
        EditText replacementEdit = dialogView.findViewById(R.id.transformReplacement);
        TextView previewLabel = dialogView.findViewById(R.id.previewLabel);
        TextView previewResult = dialogView.findViewById(R.id.previewResult);

        // Pre-fill defaults
        nameEdit.setText(defaultName);
        patternEdit.setText(defaultPattern);
        replacementEdit.setText(defaultReplacement);

        // TextWatcher to update preview when pattern or replacement changes
        TextWatcher previewWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateTransformPreview(patternEdit.getText().toString(),
                        replacementEdit.getText().toString(),
                        previewLabel, previewResult);
            }
        };

        patternEdit.addTextChangedListener(previewWatcher);
        replacementEdit.addTextChangedListener(previewWatcher);
        
        // Trigger initial preview
        updateTransformPreview(defaultPattern, defaultReplacement, previewLabel, previewResult);

        new AlertDialog.Builder(this)
            .setTitle(R.string.add_transform)
            .setView(dialogView)
            .setPositiveButton(R.string.add, (dialog, which) -> {
                String name = nameEdit.getText().toString().trim();
                String pattern = patternEdit.getText().toString();
                String replacement = replacementEdit.getText().toString();

                if (name.isEmpty() || pattern.isEmpty()) {
                    Toast.makeText(this, R.string.name_and_pattern_required, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!UrlProcessor.isValidPattern(pattern)) {
                    Toast.makeText(this, R.string.invalid_regex, Toast.LENGTH_SHORT).show();
                    return;
                }

                Transform newTransform = new Transform(name, pattern, replacement, true);
                
                // Ask whether to save to config
                new AlertDialog.Builder(this)
                    .setTitle(R.string.save_transform)
                    .setMessage(R.string.save_transform_message)
                    .setPositiveButton(R.string.save, (d, w) -> {
                        transforms.add(newTransform);
                        configRepository.saveTransforms(transforms);
                        processUrl();
                    })
                    .setNegativeButton(R.string.this_time_only, (d, w) -> {
                        // Add to transforms list but don't save
                        transforms.add(newTransform);
                        processUrl();
                    })
                    .show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Update the preview in the add transform dialog.
     */
    private void updateTransformPreview(String pattern, String replacement, 
            TextView previewLabel, TextView previewResult) {
        if (pattern.isEmpty()) {
            previewLabel.setVisibility(View.GONE);
            previewResult.setVisibility(View.GONE);
            return;
        }

        try {
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(originalUrl);
            if (matcher.find()) {
                String result = matcher.replaceAll(replacement);
                previewLabel.setVisibility(View.VISIBLE);
                previewResult.setVisibility(View.VISIBLE);
                previewResult.setText(result);
                previewResult.setTextColor(ContextCompat.getColor(this, R.color.primary));
            } else {
                previewLabel.setVisibility(View.VISIBLE);
                previewResult.setVisibility(View.VISIBLE);
                previewResult.setText(R.string.no_match);
                previewResult.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            }
        } catch (PatternSyntaxException e) {
            previewLabel.setVisibility(View.VISIBLE);
            previewResult.setVisibility(View.VISIBLE);
            previewResult.setText(R.string.invalid_regex);
            previewResult.setTextColor(Color.RED);
        }
    }

    /**
     * Show dialog when a parameter is tapped.
     * Uses styled Material buttons instead of plain text items.
     * For kept params: offer to deny (one time) or add removal regex (config).
     * For removed params: offer to allow (one time) or edit/remove the regex (config).
     */
    private void showParamActionDialog(UrlProcessor.QueryParam param) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(param.name)
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .create();

        if (param.keep) {
            // Param is currently kept - offer to remove
            addStyledButton(layout, getString(R.string.remove_this_time), v -> {
                userRemovedParams.add(param.name);
                userRestoredParams.remove(param.name);
                processUrl();
                dialog.dismiss();
            });
            addStyledButton(layout, getString(R.string.add_removal_regex), v -> {
                showAddParamRemovalTransform(param);
                dialog.dismiss();
            });
        } else {
            // Param is currently removed - offer to allow
            addStyledButton(layout, getString(R.string.allow_this_time), v -> {
                userRestoredParams.add(param.name);
                userRemovedParams.remove(param.name);
                processUrl();
                dialog.dismiss();
            });
            if (param.removedBy != null) {
                addStyledButton(layout, getString(R.string.edit_removal_regex, param.removedBy), v -> {
                    openSettings();
                    dialog.dismiss();
                });
            }
        }

        dialog.show();
    }

    /**
     * Add a styled Material button to a LinearLayout container.
     */
    private void addStyledButton(LinearLayout container, String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(ContextCompat.getColor(this, R.color.primary));
        button.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        button.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        button.setLayoutParams(lp);
        button.setOnClickListener(listener);
        container.addView(button);
    }

    /**
     * Show dialog to add a transform that removes a specific parameter.
     */
    private void showAddParamRemovalTransform(UrlProcessor.QueryParam param) {
        String name = getString(R.string.remove_param_transform, param.name, urlHost);
        String pattern = "[?&]" + Pattern.quote(param.name) + "=[^&]*";
        String replacement = "";
        
        showAddTransformDialogWithDefaults(name, pattern, replacement);
    }

    /**
     * Adapter for unified parameter list.
     * Shows kept params with values, removed params with transform name (reason).
     * Tapping a row opens the action dialog.
     */
    private class QueryParamAdapter extends RecyclerView.Adapter<QueryParamAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_query_param, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UrlProcessor.QueryParam param = queryParams.get(position);

            // Show param name
            holder.name.setText(param.name);

            if (param.keep) {
                // Kept param: show value, no strikethrough
                holder.value.setText(param.value);
                holder.name.setPaintFlags(holder.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                holder.value.setPaintFlags(holder.value.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                holder.name.setTextColor(ContextCompat.getColor(ProcessingActivity.this, R.color.text_dark));
                holder.value.setTextColor(ContextCompat.getColor(ProcessingActivity.this, android.R.color.secondary_text_light));
            } else {
                // Removed param: show reason (transform name) instead of value
                if (param.removedBy != null) {
                    holder.value.setText(param.removedBy);
                } else {
                    holder.value.setText(R.string.removed_by_user);
                }
                holder.name.setPaintFlags(holder.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.value.setPaintFlags(holder.value.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                holder.name.setTextColor(ContextCompat.getColor(ProcessingActivity.this, android.R.color.darker_gray));
                holder.value.setTextColor(ContextCompat.getColor(ProcessingActivity.this, android.R.color.darker_gray));
            }

            // Checkbox for keep/remove
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(param.keep);
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    userRestoredParams.add(param.name);
                    userRemovedParams.remove(param.name);
                } else {
                    userRemovedParams.add(param.name);
                    userRestoredParams.remove(param.name);
                }
                processUrl();
            });

            // Tap row to show action dialog
            holder.itemView.setOnClickListener(v -> showParamActionDialog(param));
        }

        @Override
        public int getItemCount() {
            return queryParams != null ? queryParams.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            TextView value;
            CheckBox checkbox;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.paramName);
                value = itemView.findViewById(R.id.paramValue);
                checkbox = itemView.findViewById(R.id.paramCheckbox);
            }
        }
    }
}
