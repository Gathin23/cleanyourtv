package com.gathin.tvhome;

import android.content.Context;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FileBrowserAdapter extends RecyclerView.Adapter<FileBrowserAdapter.ViewHolder> {

    public static class FileItem {
        public static final int TYPE_RESTORE = 0;
        public static final int TYPE_PARENT = 1;
        public static final int TYPE_FOLDER = 2;
        public static final int TYPE_IMAGE = 3;

        public final int type;
        public final File file;
        public final String name;
        public final String subtitle;

        public FileItem(int type, File file, String name, String subtitle) {
            this.type = type;
            this.file = file;
            this.name = name;
            this.subtitle = subtitle;
        }
    }

    public interface OnFileBrowserListener {
        void onImageSelected(File imageFile);
        void onRestoreDefault();
        void onDirectoryChanged(File dir, int itemCount);
    }

    private final Context ctx;
    private final File rootDir;
    private File currentDir;
    private boolean hasCustomWallpaper = false;
    private final List<FileItem> items = new ArrayList<>();
    private OnFileBrowserListener listener;
    private RecyclerView recyclerView;

    public FileBrowserAdapter(Context ctx) {
        this.ctx = ctx;
        File ext = Environment.getExternalStorageDirectory();
        if (ext == null || !ext.exists()) {
            ext = new File("/sdcard");
        }
        this.rootDir = ext;
        this.currentDir = ext;
    }

    public void setListener(OnFileBrowserListener listener) {
        this.listener = listener;
    }

    public void setHasCustomWallpaper(boolean hasCustom) {
        this.hasCustomWallpaper = hasCustom;
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public void setCurrentDir(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            this.currentDir = dir;
        }
    }

    public void resetToRoot() {
        this.currentDir = rootDir;
    }

    public boolean canNavigateUp() {
        if (currentDir == null || currentDir.equals(rootDir)) return false;
        File parent = currentDir.getParentFile();
        return parent != null && parent.canRead();
    }

    public boolean navigateUp() {
        if (canNavigateUp()) {
            currentDir = currentDir.getParentFile();
            reload();
            focusPosition(0);
            return true;
        }
        return false;
    }

    public void reload() {
        items.clear();

        if (hasCustomWallpaper) {
            items.add(new FileItem(FileItem.TYPE_RESTORE, null, "Restore Default Wallpaper", "Reset to original launcher wallpaper"));
        }

        if (canNavigateUp()) {
            items.add(new FileItem(FileItem.TYPE_PARENT, currentDir.getParentFile(), "..", "Parent folder"));
        }

        File[] files = currentDir != null ? currentDir.listFiles() : null;
        if (files != null) {
            List<File> dirs = new ArrayList<>();
            List<File> images = new ArrayList<>();

            for (File f : files) {
                if (f.isHidden()) continue;
                if (f.isDirectory() && f.canRead()) {
                    dirs.add(f);
                } else if (f.isFile() && isImageFile(f)) {
                    images.add(f);
                }
            }

            Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            Collections.sort(images, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            for (File d : dirs) {
                items.add(new FileItem(FileItem.TYPE_FOLDER, d, d.getName(), "Folder"));
            }

            for (File img : images) {
                String sizeStr = formatFileSize(img.length());
                items.add(new FileItem(FileItem.TYPE_IMAGE, img, img.getName(), sizeStr));
            }
        }

        notifyDataSetChanged();

        if (listener != null) {
            listener.onDirectoryChanged(currentDir, items.size());
        }
    }

    public void focusPosition(int pos) {
        if (recyclerView == null || items.isEmpty()) return;
        final int target = Math.max(0, Math.min(pos, items.size() - 1));
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        View view = lm != null ? lm.findViewByPosition(target) : null;
        if (view != null) {
            view.requestFocus();
        } else {
            recyclerView.scrollToPosition(target);
            recyclerView.post(() -> {
                RecyclerView.LayoutManager lmPost = recyclerView.getLayoutManager();
                View v = lmPost != null ? lmPost.findViewByPosition(target) : null;
                if (v != null) {
                    v.requestFocus();
                } else {
                    recyclerView.postDelayed(() -> {
                        RecyclerView.LayoutManager lmRetry = recyclerView.getLayoutManager();
                        View vr = lmRetry != null ? lmRetry.findViewByPosition(target) : null;
                        if (vr != null) vr.requestFocus();
                    }, 60);
                }
            });
        }
    }

    private static boolean isImageFile(File f) {
        String name = f.getName().toLowerCase(Locale.US);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".webp") || name.endsWith(".bmp");
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_file_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = items.get(position);
        holder.fileName.setText(item.name);
        holder.fileSubtitle.setText(item.subtitle);

        switch (item.type) {
            case FileItem.TYPE_RESTORE:
                holder.fileIcon.setImageResource(R.drawable.ic_restore);
                holder.fileIcon.setColorFilter(0xFFFFB300); // Amber / gold
                holder.fileBadge.setVisibility(View.GONE);
                break;
            case FileItem.TYPE_PARENT:
                holder.fileIcon.setImageResource(R.drawable.ic_folder);
                holder.fileIcon.setColorFilter(0xCCFFFFFF);
                holder.fileBadge.setVisibility(View.GONE);
                break;
            case FileItem.TYPE_FOLDER:
                holder.fileIcon.setImageResource(R.drawable.ic_folder);
                holder.fileIcon.setColorFilter(0xFFFFCA28); // Folder yellow
                holder.fileBadge.setVisibility(View.VISIBLE);
                holder.fileBadge.setText(">");
                break;
            case FileItem.TYPE_IMAGE:
            default:
                holder.fileIcon.setImageResource(R.drawable.ic_image);
                holder.fileIcon.setColorFilter(0xFF81D4FA); // Light blue
                holder.fileBadge.setVisibility(View.GONE);
                break;
        }

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                SoundManager.getInstance(ctx).playNavigation();
            }
        });

        holder.itemView.setOnClickListener(v -> {
            SoundManager.getInstance(ctx).playEnter();
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos < 0 || adapterPos >= items.size()) return;
            FileItem clicked = items.get(adapterPos);

            if (clicked.type == FileItem.TYPE_RESTORE) {
                if (listener != null) listener.onRestoreDefault();
            } else if (clicked.type == FileItem.TYPE_PARENT) {
                navigateUp();
            } else if (clicked.type == FileItem.TYPE_FOLDER) {
                setCurrentDir(clicked.file);
                reload();
                focusPosition(0);
            } else if (clicked.type == FileItem.TYPE_IMAGE) {
                if (listener != null) listener.onImageSelected(clicked.file);
            }
        });

        // Wrap-around D-Pad vertical navigation
        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos < 0) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && adapterPos == 0) {
                    focusPosition(items.size() - 1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && adapterPos == items.size() - 1) {
                    focusPosition(0);
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView fileIcon;
        final TextView fileName;
        final TextView fileSubtitle;
        final TextView fileBadge;

        ViewHolder(View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileName = itemView.findViewById(R.id.fileName);
            fileSubtitle = itemView.findViewById(R.id.fileSubtitle);
            fileBadge = itemView.findViewById(R.id.fileBadge);
        }
    }
}
