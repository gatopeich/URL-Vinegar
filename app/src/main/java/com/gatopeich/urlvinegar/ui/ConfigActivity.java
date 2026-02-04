package com.gatopeich.urlvinegar.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gatopeich.urlvinegar.R;
import com.gatopeich.urlvinegar.data.AllowedParameter;
import com.gatopeich.urlvinegar.data.ConfigRepository;
import com.gatopeich.urlvinegar.data.Transform;
import com.gatopeich.urlvinegar.util.UrlProcessor;

import java.util.Collections;
import java.util.List;

/**
 * Configuration Activity - Manage transforms and allowed parameters.
 * Implements requirements from Section 5: Configuration
 */
public class ConfigActivity extends AppCompatActivity {

    private ConfigRepository configRepository;
    private List<Transform> transforms;
    private List<AllowedParameter> allowedParameters;

    private RecyclerView transformsRecyclerView;
    private RecyclerView paramsRecyclerView;
    private TransformConfigAdapter transformAdapter;
    private AllowedParamAdapter paramAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        configRepository = ConfigRepository.getInstance(this);
        transforms = configRepository.loadTransforms();
        allowedParameters = configRepository.loadAllowedParameters();

        setupViews();
    }

    private void setupViews() {
        transformsRecyclerView = findViewById(R.id.transformsRecyclerView);
        paramsRecyclerView = findViewById(R.id.paramsRecyclerView);

        // Setup transforms RecyclerView with drag-and-drop
        transformAdapter = new TransformConfigAdapter();
        transformsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transformsRecyclerView.setAdapter(transformAdapter);

        // Requirement 5.2: Reorder transforms
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
                configRepository.saveTransforms(transforms);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe
            }
        });
        touchHelper.attachToRecyclerView(transformsRecyclerView);
        transformAdapter.setTouchHelper(touchHelper);

        // Setup allowed params RecyclerView
        paramAdapter = new AllowedParamAdapter();
        paramsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        paramsRecyclerView.setAdapter(paramAdapter);

        // Add buttons
        findViewById(R.id.addTransformButton).setOnClickListener(v -> showAddTransformDialog());
        findViewById(R.id.addParamButton).setOnClickListener(v -> showAddParamDialog());
    }

    /**
     * Requirement 5.2: Add new transforms
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

                transforms.add(new Transform(name, pattern, replacement, true));
                configRepository.saveTransforms(transforms);
                transformAdapter.notifyItemInserted(transforms.size() - 1);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 5.2: Edit existing transforms
     */
    private void showEditTransformDialog(int position) {
        Transform transform = transforms.get(position);
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transform, null);
        EditText nameEdit = dialogView.findViewById(R.id.transformName);
        EditText patternEdit = dialogView.findViewById(R.id.transformPattern);
        EditText replacementEdit = dialogView.findViewById(R.id.transformReplacement);

        nameEdit.setText(transform.getName());
        patternEdit.setText(transform.getPattern());
        replacementEdit.setText(transform.getReplacement());

        new AlertDialog.Builder(this)
            .setTitle(R.string.edit_transform)
            .setView(dialogView)
            .setPositiveButton(R.string.save, (dialog, which) -> {
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

                transform.setName(name);
                transform.setPattern(pattern);
                transform.setReplacement(replacement);
                configRepository.saveTransforms(transforms);
                transformAdapter.notifyItemChanged(position);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 5.2: Delete existing transforms
     */
    private void showDeleteTransformDialog(int position) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_transform)
            .setMessage(R.string.delete_transform_confirm)
            .setPositiveButton(R.string.delete, (dialog, which) -> {
                transforms.remove(position);
                configRepository.saveTransforms(transforms);
                transformAdapter.notifyItemRemoved(position);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 5.3: Add new allowed parameters
     */
    private void showAddParamDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_param, null);
        EditText nameEdit = dialogView.findViewById(R.id.paramName);
        EditText descEdit = dialogView.findViewById(R.id.paramDescription);

        new AlertDialog.Builder(this)
            .setTitle(R.string.add_parameter)
            .setView(dialogView)
            .setPositiveButton(R.string.add, (dialog, which) -> {
                String name = nameEdit.getText().toString().trim();
                String description = descEdit.getText().toString().trim();

                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show();
                    return;
                }

                allowedParameters.add(new AllowedParameter(name, description));
                configRepository.saveAllowedParameters(allowedParameters);
                paramAdapter.notifyItemInserted(allowedParameters.size() - 1);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 5.3: Edit existing allowed parameters
     */
    private void showEditParamDialog(int position) {
        AllowedParameter param = allowedParameters.get(position);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_param, null);
        EditText nameEdit = dialogView.findViewById(R.id.paramName);
        EditText descEdit = dialogView.findViewById(R.id.paramDescription);

        nameEdit.setText(param.getName());
        descEdit.setText(param.getDescription());

        new AlertDialog.Builder(this)
            .setTitle(R.string.edit_parameter)
            .setView(dialogView)
            .setPositiveButton(R.string.save, (dialog, which) -> {
                String name = nameEdit.getText().toString().trim();
                String description = descEdit.getText().toString().trim();

                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show();
                    return;
                }

                param.setName(name);
                param.setDescription(description);
                configRepository.saveAllowedParameters(allowedParameters);
                paramAdapter.notifyItemChanged(position);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Requirement 5.3: Delete existing allowed parameters
     */
    private void showDeleteParamDialog(int position) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_parameter)
            .setMessage(R.string.delete_param_confirm)
            .setPositiveButton(R.string.delete, (dialog, which) -> {
                allowedParameters.remove(position);
                configRepository.saveAllowedParameters(allowedParameters);
                paramAdapter.notifyItemRemoved(position);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Adapter for transform configuration list.
     */
    private class TransformConfigAdapter extends RecyclerView.Adapter<TransformConfigAdapter.ViewHolder> {
        private ItemTouchHelper touchHelper;

        void setTouchHelper(ItemTouchHelper helper) {
            this.touchHelper = helper;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transform_config, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transform transform = transforms.get(position);

            holder.name.setText(transform.getName());
            holder.pattern.setText(transform.getPattern());

            // Enable/disable checkbox
            holder.checkbox.setOnCheckedChangeListener(null);
            holder.checkbox.setChecked(transform.isEnabled());
            holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                transform.setEnabled(isChecked);
                configRepository.saveTransforms(transforms);
            });

            // Edit button
            holder.editButton.setOnClickListener(v -> showEditTransformDialog(position));

            // Delete button
            holder.deleteButton.setOnClickListener(v -> showDeleteTransformDialog(position));

            // Drag handle
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return transforms.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            TextView pattern;
            CheckBox checkbox;
            ImageButton editButton;
            ImageButton deleteButton;
            ImageView dragHandle;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.transformName);
                pattern = itemView.findViewById(R.id.transformPattern);
                checkbox = itemView.findViewById(R.id.transformCheckbox);
                editButton = itemView.findViewById(R.id.editButton);
                deleteButton = itemView.findViewById(R.id.deleteButton);
                dragHandle = itemView.findViewById(R.id.dragHandle);
            }
        }
    }

    /**
     * Adapter for allowed parameters list.
     */
    private class AllowedParamAdapter extends RecyclerView.Adapter<AllowedParamAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_allowed_param, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AllowedParameter param = allowedParameters.get(position);

            holder.name.setText(param.getName());
            holder.description.setText(param.getDescription());
            holder.description.setVisibility(
                param.getDescription().isEmpty() ? View.GONE : View.VISIBLE);

            // Edit button
            holder.editButton.setOnClickListener(v -> showEditParamDialog(position));

            // Delete button
            holder.deleteButton.setOnClickListener(v -> showDeleteParamDialog(position));
        }

        @Override
        public int getItemCount() {
            return allowedParameters.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            TextView description;
            ImageButton editButton;
            ImageButton deleteButton;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.paramName);
                description = itemView.findViewById(R.id.paramDescription);
                editButton = itemView.findViewById(R.id.editButton);
                deleteButton = itemView.findViewById(R.id.deleteButton);
            }
        }
    }
}
