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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gatopeich.urlvinegar.R;
import com.gatopeich.urlvinegar.data.ConfigRepository;
import com.gatopeich.urlvinegar.data.Transform;
import com.gatopeich.urlvinegar.util.UrlProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Processing Activity - Handles URL cleaning dialog.
 * Implements requirements from Section 3: Processing Dialog
 */
public class ProcessingActivity extends AppCompatActivity {

    private ConfigRepository configRepository;
    private List<Transform> transforms;
    private List<Transform> matchingTransforms; // Only transforms that match the URL
    private Set<Integer> disabledForThisUrl; // Track disabled transforms for this URL
    private Set<Integer> enabledForThisUrl;  // Track temporarily enabled transforms
    private List<UrlProcessor.QueryParam> queryParams;

    private String originalUrl;
    private String currentUrl;
    private String urlHost;
    private boolean isProcessTextIntent;
    private Set<String> userRemovedParams; // Track params the user explicitly unchecked

    private TextView urlPreview;
    private RecyclerView transformsRecyclerView;
    private RecyclerView paramsRecyclerView;
    private LinearLayout transformsSection;
    private LinearLayout paramsSection;
    private TransformAdapter transformAdapter;
    private QueryParamAdapter paramAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        configRepository = ConfigRepository.getInstance(this);
        transforms = configRepository.loadTransforms();
        matchingTransforms = new ArrayList<>();
        disabledForThisUrl = new HashSet<>();
        enabledForThisUrl = new HashSet<>();
        userRemovedParams = new HashSet<>();

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
        transformsRecyclerView = findViewById(R.id.transformsRecyclerView);
        paramsRecyclerView = findViewById(R.id.paramsRecyclerView);
        transformsSection = findViewById(R.id.transformsSection);
        paramsSection = findViewById(R.id.paramsSection);

        // Setup transforms RecyclerView with drag-and-drop
        transformAdapter = new TransformAdapter();
        transformsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transformsRecyclerView.setAdapter(transformAdapter);
        
        // Requirement 3.3: Drag handles for reordering
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, 
                    @NonNull RecyclerView.ViewHolder viewHolder, 
                    @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                // Swap in matching transforms list
                Collections.swap(matchingTransforms, from, to);
                transformAdapter.notifyItemMoved(from, to);
                // Also update order in main transforms list and persist
                reorderTransformsInConfig();
                processUrl();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe support
            }
        });
        touchHelper.attachToRecyclerView(transformsRecyclerView);
        transformAdapter.setTouchHelper(touchHelper);

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
        findViewById(R.id.addTransformButton).setOnClickListener(v -> showAddTransformDialog());
    }

    /**
     * Reorder transforms in the main config list based on current matching transforms order.
     */
    private void reorderTransformsInConfig() {
        // For simplicity, we update the enabled transforms order
        // by rebuilding the transforms list with matching transforms first
        List<Transform> newOrder = new ArrayList<>();
        Set<Integer> addedIndices = new HashSet<>();
        
        // Add matching transforms first in their new order
        for (Transform t : matchingTransforms) {
            int idx = transforms.indexOf(t);
            if (idx >= 0 && !addedIndices.contains(idx)) {
                newOrder.add(t);
                addedIndices.add(idx);
            }
        }
        
        // Add remaining non-matching transforms
        for (int i = 0; i < transforms.size(); i++) {
            if (!addedIndices.contains(i)) {
                newOrder.add(transforms.get(i));
            }
        }
        
        // Replace transforms list with new order
        transforms = newOrder;
        configRepository.saveTransforms(transforms);
    }

    private void processUrl() {
        // Build list of matching transforms
        matchingTransforms.clear();
        for (Transform transform : transforms) {
            if (UrlProcessor.transformMatches(originalUrl, transform)) {
                matchingTransforms.add(transform);
            }
        }

        // Build set of disabled indices for processing
        Set<Integer> processDisabled = new HashSet<>();
        for (int i = 0; i < transforms.size(); i++) {
            // Disabled if: in disabledForThisUrl AND not in enabledForThisUrl
            if (disabledForThisUrl.contains(i) && !enabledForThisUrl.contains(i)) {
                processDisabled.add(i);
            }
        }

        // Apply transforms
        UrlProcessor.ProcessResult result = UrlProcessor.applyTransforms(
            originalUrl, transforms, processDisabled);
        
        currentUrl = result.url;
        
        // Parse query parameters - default all to KEPT (enabled)
        // Then apply any user overrides (params they explicitly unchecked)
        queryParams = UrlProcessor.parseQueryParams(currentUrl, new HashSet<>());
        for (UrlProcessor.QueryParam p : queryParams) {
            p.keep = !userRemovedParams.contains(p.name);
        }
        
        // Reconstruct URL with filtered parameters
        currentUrl = UrlProcessor.reconstructUrl(currentUrl, queryParams);
        
        // Update UI
        updatePreview(result);
        updateSectionVisibility();
        transformAdapter.notifyDataSetChanged();
        paramAdapter.notifyDataSetChanged();
    }

    /**
     * Show/hide sections based on content availability.
     */
    private void updateSectionVisibility() {
        // Show transforms section only if there are matching transforms
        if (matchingTransforms.isEmpty()) {
            transformsSection.setVisibility(View.GONE);
        } else {
            transformsSection.setVisibility(View.VISIBLE);
        }

        // Show params section only if there are query params
        if (queryParams == null || queryParams.isEmpty()) {
            paramsSection.setVisibility(View.GONE);
        } else {
            paramsSection.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Requirement 3.1: Show preview of cleaned URL
     * Requirement 4.3: URL Reconstruction - Invalid scheme should be red
     */
    private void updatePreview(UrlProcessor.ProcessResult result) {
        urlPreview.setText(currentUrl);
        
        if (!result.isValid) {
            urlPreview.setTextColor(Color.RED);
            findViewById(R.id.shareButton).setEnabled(false);
        } else {
            urlPreview.setTextColor(ContextCompat.getColor(this, R.color.text_dark));
            findViewById(R.id.shareButton).setEnabled(true);
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
     * Requirement 3.4: Transform Toggling
     */
    private void showDisableTransformDialog(int position, Transform transform) {
        // Find the actual position in the main transforms list
        int mainPosition = transforms.indexOf(transform);
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.disable_transform)
            .setItems(new CharSequence[]{
                getString(R.string.this_url_only),
                getString(R.string.disable_in_config),
                getString(R.string.delete_from_config)
            }, (dialog, which) -> {
                switch (which) {
                    case 0: // This URL only - just mark as disabled temporarily
                        disabledForThisUrl.add(mainPosition);
                        enabledForThisUrl.remove(mainPosition);
                        break;
                    case 1: // Disable in config
                        transform.setEnabled(false);
                        configRepository.saveTransforms(transforms);
                        break;
                    case 2: // Delete from config
                        transforms.remove(transform);
                        configRepository.saveTransforms(transforms);
                        break;
                }
                processUrl();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
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
     * Adapter for transform list.
     * Requirement 3.2: Transform List - Only shows matching transforms
     */
    private class TransformAdapter extends RecyclerView.Adapter<TransformAdapter.ViewHolder> {
        private ItemTouchHelper touchHelper;

        void setTouchHelper(ItemTouchHelper helper) {
            this.touchHelper = helper;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transform, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transform transform = matchingTransforms.get(position);
            int mainPosition = transforms.indexOf(transform);
            
            // Check if disabled for this URL (temporary disable)
            boolean tempDisabled = disabledForThisUrl.contains(mainPosition) && 
                                   !enabledForThisUrl.contains(mainPosition);
            boolean effectivelyEnabled = transform.isEnabled() && !tempDisabled;

            // Requirement 3.2: Show name and pattern
            holder.name.setText(transform.getName());
            holder.pattern.setText(transform.getPattern());

            // All displayed transforms match, so always full opacity
            holder.itemView.setAlpha(1.0f);

            // Requirement 7.2: Disabled transforms with strikethrough
            if (!effectivelyEnabled) {
                holder.name.setPaintFlags(holder.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                holder.name.setPaintFlags(holder.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            // Requirement 3.2: Checkbox for enable/disable
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(effectivelyEnabled);
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) {
                    // Requirement 3.4: Prompt when disabling
                    showDisableTransformDialog(position, transform);
                    holder.checkbox.setChecked(true); // Revert until user decides
                } else {
                    // Re-enabling: remove from disabled set, add to enabled set
                    disabledForThisUrl.remove(mainPosition);
                    enabledForThisUrl.add(mainPosition);
                    if (!transform.isEnabled()) {
                        transform.setEnabled(true);
                        configRepository.saveTransforms(transforms);
                    }
                    processUrl();
                }
            });

            // Requirement 3.3 & 7.3: Drag handles
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder);
                }
                return false;
            });

            // Highlight invalid regex in red
            if (!UrlProcessor.isValidPattern(transform.getPattern())) {
                holder.pattern.setTextColor(Color.RED);
            } else {
                holder.pattern.setTextColor(ContextCompat.getColor(ProcessingActivity.this, android.R.color.secondary_text_light));
            }
        }

        @Override
        public int getItemCount() {
            return matchingTransforms.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            TextView pattern;
            CheckBox checkbox;
            ImageView dragHandle;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.transformName);
                pattern = itemView.findViewById(R.id.transformPattern);
                checkbox = itemView.findViewById(R.id.transformCheckbox);
                dragHandle = itemView.findViewById(R.id.dragHandle);
            }
        }
    }

    /**
     * Adapter for query parameter list.
     * Shows parameters with strikethrough when not kept, and (+) button to add removal transform.
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

            // Show name and value
            holder.name.setText(param.name);
            holder.value.setText(param.value);

            // Strikethrough for params that will be removed
            if (!param.keep) {
                holder.name.setPaintFlags(holder.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.value.setPaintFlags(holder.value.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.name.setTextColor(ContextCompat.getColor(ProcessingActivity.this, android.R.color.darker_gray));
                // Show cog button for unchecked params to add removal regex
                holder.addTransformBtn.setVisibility(View.VISIBLE);
            } else {
                holder.name.setPaintFlags(holder.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                holder.value.setPaintFlags(holder.value.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                holder.name.setTextColor(ContextCompat.getColor(ProcessingActivity.this, R.color.text_dark));
                holder.addTransformBtn.setVisibility(View.GONE);
            }

            // Checkbox for keep/remove
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(param.keep);
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                param.keep = isChecked;
                if (isChecked) {
                    userRemovedParams.remove(param.name);
                } else {
                    userRemovedParams.add(param.name);
                }
                processUrl();
            });

            // Add transform button click
            holder.addTransformBtn.setOnClickListener(v -> showAddParamRemovalTransform(param));
        }

        @Override
        public int getItemCount() {
            return queryParams != null ? queryParams.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            TextView value;
            CheckBox checkbox;
            ImageButton addTransformBtn;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.paramName);
                value = itemView.findViewById(R.id.paramValue);
                checkbox = itemView.findViewById(R.id.paramCheckbox);
                addTransformBtn = itemView.findViewById(R.id.addTransformBtn);
            }
        }
    }
}
