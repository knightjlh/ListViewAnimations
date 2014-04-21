package com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.undo;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

import com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.SwipeDismissTouchListener;
import com.nhaarman.listviewanimations.util.AdapterViewUtil;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.SwipeDismissTouchListener} that adds an undo stage to the item swiping.
 */
public class SwipeUndoTouchListener extends SwipeDismissTouchListener {

    /**
     * The callback which gets notified of events.
     */
    private final UndoCallback mCallback;

    /**
     * The positions that are in the undo state.
     */
    private final Collection<Integer> mUndoPositions = new LinkedList<Integer>();

    /**
     * The positions that have been dismissed.
     */
    private final List<Integer> mDismissedPositions = new LinkedList<Integer>();

    /**
     * The {@link View}s that have been dismissed.
     */
    private final Collection<View> mDismissedViews = new LinkedList<View>();

    /**
     * Constructs a new {@code SwipeDismissTouchListener} for the given {@link android.widget.AbsListView}.

     * @param absListView
     *            The {@code AbsListView} whose items should be dismissable.
     */
    @SuppressWarnings("UnnecessaryFullyQualifiedName")
    public SwipeUndoTouchListener(final AbsListView absListView, final UndoCallback callback) {
        super(absListView, callback);
        mCallback = callback;
    }

    @Override
    public void afterViewFling(final View view, final int position) {
        if (mUndoPositions.contains(position)) {
            mUndoPositions.remove(position);
            performDismiss(view, position);
            hideUndoView(view);
        } else {
            mUndoPositions.add(position);
            mCallback.onUndoShown(view, position);
            showUndoView(view);
            restoreViewPresentation(view);
        }
    }

    @Override
    protected void afterCancelSwipe(final View view, final int position) {
        finalizeDismiss();
    }

    /**
     * Animates the dismissed list item to zero-height and fires the dismiss callback when all dismissed list item animations have completed.
     * @param view the dismissed {@link View}.
     */
    @Override
    protected void performDismiss(final View view, final int position) {
        super.performDismiss(view, position);

        mDismissedViews.add(view);
        mDismissedPositions.add(position);

        mCallback.onDismiss(view, position);
    }

    /**
     * Sets the visibility of the primary {@link View} to {@link View#GONE}, and animates the undo {@code View} in to view.
     * @param view the parent {@code View} which contains both primary and undo {@code View}s.
     */
    private void showUndoView(final View view) {
        mCallback.getPrimaryView(view).setVisibility(View.GONE);

        View undoView = mCallback.getUndoView(view);
        undoView.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(undoView, "alpha", 0f, 1f).start();
    }

    /**
     * Sets the visibility of the primary {@link View} to {@link View#VISIBLE}, and that of the undo {@code View} to {@link View#GONE}.
     * @param view the parent {@code View} which contains both primary and undo {@code View}s.
     */
    private void hideUndoView(final View view) {
        mCallback.getPrimaryView(view).setVisibility(View.VISIBLE);
        mCallback.getUndoView(view).setVisibility(View.GONE);
    }

    /**
     * If necessary, notifies the {@link UndoCallback} to remove dismissed object from the adapter,
     * and restores the {@link View} presentations.
     */
    @Override
    protected void finalizeDismiss() {
        if (getActiveDismissCount() == 0 && getActiveSwipeCount() == 0) {
            restoreViewPresentations(mDismissedViews);
            notifyCallback(mDismissedPositions);

            Collection<Integer> newUndoPositions = Util.processDeletions(mUndoPositions, mDismissedPositions);
            mUndoPositions.clear();
            mUndoPositions.addAll(newUndoPositions);

            mDismissedViews.clear();
            mDismissedPositions.clear();
        }
    }

    /**
     * Restores the height of given {@code View}.
     * Also calls its super implementation.
     */
    @Override
    protected void restoreViewPresentation(final View view) {
        super.restoreViewPresentation(view);
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.height = 0;
        view.setLayoutParams(layoutParams);
    }

    /**
     * Performs the undo animation and restores the original state for given {@link View}.
     * @param view the parent {@code View} which contains both primary and undo {@code View}s.
     */
    public void undo(final View view) {
        int position = AdapterViewUtil.getPositionForView(getAbsListView(), view);
        mUndoPositions.remove(position);

        View primaryView = mCallback.getPrimaryView(view);
        View undoView = mCallback.getUndoView(view);

        primaryView.setVisibility(View.VISIBLE);

        ObjectAnimator undoAlphaAnimator = ObjectAnimator.ofFloat(undoView, "alpha", 1f, 0f);
        ObjectAnimator primaryAlphaAnimator = ObjectAnimator.ofFloat(primaryView, "alpha", 0f, 1f);
        ObjectAnimator primaryXAnimator = ObjectAnimator.ofFloat(primaryView, "translationX", primaryView.getWidth(), 0f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(undoAlphaAnimator, primaryAlphaAnimator, primaryXAnimator);
        animatorSet.addListener(new UndoAnimatorListener(undoView));
        animatorSet.start();
    }

    @Override
    protected void directDismiss(final int position) {
        mDismissedPositions.add(position);
        finalizeDismiss();
    }

    /**
     * An {@link com.nineoldandroids.animation.Animator.AnimatorListener} which finalizes the undo when the animation is finished.
     */
    private class UndoAnimatorListener extends AnimatorListenerAdapter {
        private final View mUndoView;

        UndoAnimatorListener(final View undoView) {
            mUndoView = undoView;
        }

        @Override
        public void onAnimationEnd(final Animator animation) {
            mUndoView.setVisibility(View.GONE);
            finalizeDismiss();
        }
    }
}
