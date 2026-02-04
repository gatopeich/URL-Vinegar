package com.gatopeich.urlvinegar.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.gatopeich.urlvinegar.data.AllowedParameter;
import com.gatopeich.urlvinegar.data.ConfigRepository;
import com.gatopeich.urlvinegar.data.Transform;
import com.gatopeich.urlvinegar.util.UrlProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Processing Activity - Handles URL cleaning dialog.
 * Implements requirements from Section 3: Processing Dialog
 */
public class ProcessingActivity extends AppCompatActivity {

    private ConfigRepository configRepository;
    private List<Transform> transforms;
    private List<AllowedParameter> allowedParameters;
    private Set<String> allowedParamNames;
    private Set<Integer> disabledForThisUrl;
    private List<UrlProcessor.QueryParam> queryParams;

    private String originalUrl;
    private String currentUrl;

    private TextView urlPreview;
    private RecyclerView transformsRecyclerView;
    private RecyclerView paramsRecyclerView;
    private TransformAdapter transformAdapter;
    private QueryParamAdapter paramAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        configRepository = ConfigRepository.getInstance(this);
        transforms = configRepository.loadTransforms();
        allowedParameters = configRepository.loadAllowedParameters();
        disabledForThisUrl = new HashSet<>();
        
        // Build set of allowed parameter names
        allowedParamNames = new HashSet<>();
        for (AllowedParameter param : allowedParameters) {
            allowedParamNames.add(param.getName());
        }

        // Extract URL from intent
        originalUrl = extractUrlFromIntent(getIntent());
        if (originalUrl == null) {
            // No URL found, open config activity instead
            startActivity(new Intent(this, ConfigActivity.class));
            finish();
            return;
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
        
        return null;
    }

    private void setupViews() {
        urlPreview = findViewById(R.id.urlPreview);
        transformsRecyclerView = findViewById(R.id.transformsRecyclerView);
        paramsRecyclerView = findViewById(R.id.paramsRecyclerView);

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
                Collections.swap(transforms, from, to);
                transformAdapter.notifyItemMoved(from, to);
                // Requirement 3.3: Persist new order
                configRepository.saveTransforms(transforms);
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
        findViewById(R.id.shareButton).setOnClickListener(v -> shareUrl());
        findViewById(R.id.copyButton).setOnClickListener(v -> copyUrl());
        findViewById(R.id.cancelButton).setOnClickListener(v -> finish());
        findViewById(R.id.settingsButton).setOnClickListener(v -> openSettings());
        findViewById(R.id.addTransformButton).setOnClickListener(v -> showAddTransformDialog());
    }

    private void processUrl() {
        // Apply transforms
        UrlProcessor.ProcessResult result = UrlProcessor.applyTransforms(
            originalUrl, transforms, disabledForThisUrl);
        
        currentUrl = result.url;
        
        // Parse query parameters
        queryParams = UrlProcessor.parseQueryParams(currentUrl, allowedParamNames);
        
        // Reconstruct URL with filtered parameters
        currentUrl = UrlProcessor.reconstructUrl(currentUrl, queryParams);
        
        // Update UI
        updatePreview(result);
        transformAdapter.notifyDataSetChanged();
        paramAdapter.notifyDataSetChanged();
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
            urlPreview.setTextColor(ContextCompat.getColor(this, android.R.color.primary_text_light));
            findViewById(R.id.shareButton).setEnabled(true);
        }
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
     */
    private void showAddTransformDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transform, null);
        EditText nameEdit = dialogView.findViewById(R.id.transformName);
        EditText patternEdit = dialogView.findViewById(R.id.transformPattern);
        EditText replacementEdit = dialogView.findViewById(R.id.transformReplacement);

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
                        transforms.add(newTransform);
                        processUrl();
                    })
                    .show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 3.4: Transform Toggling
     */
    private void showDisableTransformDialog(int position, Transform transform) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.disable_transform)
            .setItems(new CharSequence[]{
                getString(R.string.this_url_only),
                getString(R.string.disable_in_config),
                getString(R.string.delete_from_config)
            }, (dialog, which) -> {
                switch (which) {
                    case 0: // This URL only
                        disabledForThisUrl.add(position);
                        break;
                    case 1: // Disable in config
                        transform.setEnabled(false);
                        configRepository.saveTransforms(transforms);
                        break;
                    case 2: // Delete from config
                        transforms.remove(position);
                        configRepository.saveTransforms(transforms);
                        break;
                }
                processUrl();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 3.7: Query Parameter Toggling - Keep parameter
     */
    private void showKeepParamDialog(UrlProcessor.QueryParam param) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.keep_parameter)
            .setItems(new CharSequence[]{
                getString(R.string.this_time_only),
                getString(R.string.add_to_whitelist)
            }, (dialog, which) -> {
                param.keep = true;
                if (which == 1) { // Add to whitelist
                    showAddToWhitelistDialog(param.name);
                }
                processUrl();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 3.7: Add to whitelist with optional description
     */
    private void showAddToWhitelistDialog(String paramName) {
        EditText descEdit = new EditText(this);
        descEdit.setHint(R.string.description_optional);

        new AlertDialog.Builder(this)
            .setTitle(R.string.add_to_whitelist)
            .setMessage(getString(R.string.add_param_message, paramName))
            .setView(descEdit)
            .setPositiveButton(R.string.add, (dialog, which) -> {
                String description = descEdit.getText().toString().trim();
                allowedParameters.add(new AllowedParameter(paramName, description));
                allowedParamNames.add(paramName);
                configRepository.saveAllowedParameters(allowedParameters);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 3.7: Query Parameter Toggling - Remove parameter
     */
    private void showRemoveParamDialog(UrlProcessor.QueryParam param) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.remove_parameter)
            .setItems(new CharSequence[]{
                getString(R.string.this_time_only),
                getString(R.string.remove_from_whitelist)
            }, (dialog, which) -> {
                param.keep = false;
                if (which == 1) { // Remove from whitelist
                    allowedParamNames.remove(param.name);
                    // Find and remove from allowed parameters list
                    for (int i = 0; i < allowedParameters.size(); i++) {
                        if (allowedParameters.get(i).getName().equals(param.name)) {
                            allowedParameters.remove(i);
                            break;
                        }
                    }
                    configRepository.saveAllowedParameters(allowedParameters);
                }
                processUrl();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Adapter for transform list.
     * Requirement 3.2: Transform List
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
            Transform transform = transforms.get(position);
            boolean matches = UrlProcessor.transformMatches(originalUrl, transform);
            boolean disabled = disabledForThisUrl.contains(position);

            // Requirement 3.2: Show name and pattern
            holder.name.setText(transform.getName());
            holder.pattern.setText(transform.getPattern());

            // Requirement 7.2: Visual feedback - matching vs non-matching
            if (!matches) {
                holder.itemView.setAlpha(0.5f);
            } else {
                holder.itemView.setAlpha(1.0f);
            }

            // Requirement 7.2: Disabled transforms with strikethrough
            if (!transform.isEnabled() || disabled) {
                holder.name.setPaintFlags(holder.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                holder.name.setPaintFlags(holder.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            // Requirement 3.2: Checkbox for enable/disable
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(transform.isEnabled() && !disabled);
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked && transform.isEnabled()) {
                    // Requirement 3.4: Prompt when disabling
                    showDisableTransformDialog(position, transform);
                    holder.checkbox.setChecked(true); // Revert until user decides
                } else if (isChecked) {
                    // Requirement 3.4: Enable without prompting
                    disabledForThisUrl.remove(position);
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
            return transforms.size();
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
     * Requirement 3.6: Query Parameter List
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
            boolean isWhitelisted = allowedParamNames.contains(param.name);

            // Requirement 3.6: Show name and value
            holder.name.setText(param.name);
            holder.value.setText(param.value);

            // Requirement 3.6 & 7.2: Visual distinction for whitelisted params
            if (isWhitelisted) {
                holder.name.setTextColor(ContextCompat.getColor(ProcessingActivity.this, android.R.color.holo_green_dark));
            } else {
                holder.name.setTextColor(ContextCompat.getColor(ProcessingActivity.this, android.R.color.primary_text_light));
            }

            // Requirement 3.6: Checkbox for keep/remove
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(param.keep);
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !isWhitelisted) {
                    // Requirement 3.7: Prompt when keeping non-whitelisted param
                    showKeepParamDialog(param);
                    holder.checkbox.setChecked(false); // Revert until user decides
                } else if (!isChecked && isWhitelisted) {
                    // Requirement 3.7: Prompt when removing whitelisted param
                    showRemoveParamDialog(param);
                    holder.checkbox.setChecked(true); // Revert until user decides
                } else {
                    param.keep = isChecked;
                    processUrl();
                }
            });
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
