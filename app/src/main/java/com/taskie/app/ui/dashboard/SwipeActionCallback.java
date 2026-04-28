package com.taskie.app.ui.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.taskie.app.R;

/**
 * Handles swipe-left (delete) and swipe-right (complete) on task cards.
 * Draws a colored background with icon while swiping.
 */
public abstract class SwipeActionCallback extends ItemTouchHelper.SimpleCallback {

    private final Context  context;
    private final Paint    bgPaint = new Paint();

    public SwipeActionCallback(Context context) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.context = context;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView rv,
                          @NonNull RecyclerView.ViewHolder vh,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    /** Only allow swiping on TASK rows, not section headers. */
    @Override
    public int getSwipeDirs(@NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh) {
        // Section headers have view type 0, tasks have view type 1
        if (rv.getAdapter() != null && rv.getAdapter().getItemViewType(vh.getAdapterPosition()) == 0) {
            return 0;
        }
        return super.getSwipeDirs(rv, vh);
    }

    @Override
    public void onChildDraw(@NonNull Canvas c,
                            @NonNull RecyclerView rv,
                            @NonNull RecyclerView.ViewHolder vh,
                            float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {

        View itemView = vh.itemView;
        int  height   = itemView.getBottom() - itemView.getTop();

        if (dX > 0) {
            // Swipe right → green "complete" background
            bgPaint.setColor(context.getColor(R.color.swipe_complete_bg));
            RectF bg = new RectF(itemView.getLeft(), itemView.getTop(),
                                 itemView.getLeft() + dX, itemView.getBottom());
            c.drawRect(bg, bgPaint);

            Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_check_circle);
            if (icon != null) {
                int iconMargin = (height - icon.getIntrinsicHeight()) / 2;
                int iconTop    = itemView.getTop() + iconMargin;
                int iconLeft   = itemView.getLeft() + iconMargin;
                icon.setBounds(iconLeft, iconTop,
                               iconLeft + icon.getIntrinsicWidth(),
                               iconTop  + icon.getIntrinsicHeight());
                icon.draw(c);
            }
        } else if (dX < 0) {
            // Swipe left → red "delete" background
            bgPaint.setColor(context.getColor(R.color.swipe_delete_bg));
            RectF bg = new RectF(itemView.getRight() + dX, itemView.getTop(),
                                 itemView.getRight(), itemView.getBottom());
            c.drawRect(bg, bgPaint);

            Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_delete);
            if (icon != null) {
                int iconMargin = (height - icon.getIntrinsicHeight()) / 2;
                int iconTop    = itemView.getTop() + iconMargin;
                int iconRight  = itemView.getRight() - iconMargin;
                icon.setBounds(iconRight - icon.getIntrinsicWidth(), iconTop,
                               iconRight, iconTop + icon.getIntrinsicHeight());
                icon.draw(c);
            }
        }

        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
    }

}
