package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.applibrary.model.AppLibraryCategory;
import com.android.launcher3.model.data.AppInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.android.launcher3.applibrary.model.CategoryData;

public class AppLibraryDashboardAdapter extends RecyclerView.Adapter<AppLibraryDashboardAdapter.CategoryViewHolder> {

    private final Context mContext;
    private final List<CategoryData> mCategories = new ArrayList<>();

    public AppLibraryDashboardAdapter(Context context) {
        mContext = context;
    }

    public void updateData(Map<AppLibraryCategory, List<AppInfo>> categorizedApps) {
        mCategories.clear();
        for (Map.Entry<AppLibraryCategory, List<AppInfo>> entry : categorizedApps.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                mCategories.add(new CategoryData(entry.getKey(), entry.getValue()));
            }
        }
        android.util.Log.d("AppLibraryDashboardAdapter", "updated with " + mCategories.size() + " categories");
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout container = new LinearLayout(mContext);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        
        // Setup margins and size for the GridLayoutManager
        int margin = mContext.getResources().getDimensionPixelSize(R.dimen.app_library_card_margin);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(margin, margin, margin, margin);
        container.setLayoutParams(lp);
        
        CategoryCardView cardView = new CategoryCardView(mContext);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardView.setLayoutParams(cardLp);
        
        TextView label = new TextView(mContext);
        label.setTextColor(Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        // iOS uses medium or regular sans-serif
        label.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = mContext.getResources().getDimensionPixelSize(R.dimen.app_library_card_margin) / 2;
        label.setLayoutParams(labelLp);
        
        container.addView(cardView);
        container.addView(label);
        
        return new CategoryViewHolder(container, cardView, label);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryData data = mCategories.get(position);
        holder.cardView.bind(data);
        holder.label.setText(data.category.getTitle());
    }

    @Override
    public int getItemCount() {
        return mCategories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        final CategoryCardView cardView;
        final TextView label;
        
        CategoryViewHolder(@NonNull View itemView, CategoryCardView cardView, TextView label) {
            super(itemView);
            this.cardView = cardView;
            this.label = label;
        }
    }
}
