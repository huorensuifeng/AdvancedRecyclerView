package com.yalantis.phoenix;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import com.yalantis.phoenix.interfacepackage.RefreshableAndLoadable;
import com.yalantis.phoenix.refresh_view.AdvancedDrawable;
import com.yalantis.phoenix.refresh_view.SunAdvancedBottomDrawable;
import com.yalantis.phoenix.refresh_view.SunAdvancedDrawable;
import com.yalantis.phoenix.wrapper.RefreshLoadWrapper;

/**
 * Created by shijianguo on 2017/9/10.
 */

public class AdvancedDrawableRecyclerView extends RecyclerView {
    private boolean canRefresh = true;
    private boolean canLoad = false;

    private static final int DRAG_MAX_DISTANCE_V = 300;
    public static final long MAX_OFFSET_ANIMATION_DURATION = 500;
    private static final float DRAG_RATE = 0.3f;

    private float INITIAL_X = -1;
    private float INITIAL_Y = -1;
    private float lastY = 0;

    private static final String TAG = "ADR";

    private AdvancedDrawable mRefreshDrawable;
    private AdvancedDrawable mLoadDrawable;

    private boolean expectedAdapter = false;
    private boolean showRefreshFlag = false;
    private boolean showLoadFlag = false;
    private ValueAnimator animator;
    private Interpolator mInterpolator = new LinearInterpolator();
    private RefreshableAndLoadable mDataSource;
    private boolean gettingData = false;

    public AdvancedDrawableRecyclerView(Context context) {
        super(context);
        init(context);
    }

    public AdvancedDrawableRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AdvancedDrawableRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    void init(Context context){
        ViewCompat.setChildrenDrawingOrderEnabled(this, true);
        mRefreshDrawable = new SunAdvancedDrawable(context,this);
        mLoadDrawable = new SunAdvancedBottomDrawable(context,this);
    }

    public void setRefreshDrawable(AdvancedDrawable drawable){
        mRefreshDrawable = drawable;
        if (expectedAdapter){
            ((RefreshLoadWrapper) getAdapter()).setRefreshDrawable(getContext(),mRefreshDrawable);
        }
    }

    public void setLoadDrawable(AdvancedDrawable drawable){
        mLoadDrawable = drawable;
        if (expectedAdapter){
            ((RefreshLoadWrapper) getAdapter()).setRefreshDrawable(getContext(),mLoadDrawable);
        }
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        if (adapter instanceof RefreshLoadWrapper){
            Log.d(TAG,"adapter kind of RefreshLoadWrapper");
            expectedAdapter = true;
            ((RefreshLoadWrapper) adapter).setRefreshDrawable(getContext(),mRefreshDrawable);
            ((RefreshLoadWrapper) adapter).setLoadDrawable(getContext(),mLoadDrawable);
            mRefreshDrawable.setAdapter(adapter);
            mLoadDrawable.setAdapter(adapter);
        }else {
            expectedAdapter = false;
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        if (!expectedAdapter || (!canRefresh && !canLoad))return super.onTouchEvent(ev);
        if (gettingData)return true;
        final int action = MotionEventCompat.getActionMasked(ev);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (isRunning()){//
                    // can stop animation
                    stop();
                    //fix initial action_down position
                    calculateInitY(MotionEventCompat.getY(ev,0),DRAG_MAX_DISTANCE_V,DRAG_RATE,
                            showRefreshFlag ? mRefreshDrawable.getPercent() : -mLoadDrawable.getPercent());
                }else {
                    INITIAL_Y = MotionEventCompat.getY(ev,0);
                    lastY = INITIAL_Y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                final float agentY = MotionEventCompat.getY(ev,0);
                if (agentY > INITIAL_Y){
                    // towards bottom
                    if (!canChildScrollUp()){
                        if (!canRefresh)return super.onTouchEvent(ev);
                        if (showLoadFlag)showLoadFlag = false;
                        if (!showRefreshFlag){
                            showRefreshFlag = true;
                            INITIAL_Y = agentY;
                        }
                        mRefreshDrawable.setPercent(fixPercent(Math.abs(calculatePercent(INITIAL_Y,
                                agentY,DRAG_MAX_DISTANCE_V,DRAG_RATE))),true);
                        ((RefreshLoadWrapper) getAdapter()).setRefreshHeight(getViewOffset(mRefreshDrawable.getPercent()));
                        lastY = agentY;
                        return true;
                    }else {
                        if(showRefreshFlag)showRefreshFlag = false;
                        lastY = agentY;
                        break;
                    }
                } else if (agentY < INITIAL_Y){

                    if (!canChildScrollBottom()){
                        if (!canLoad)return super.onTouchEvent(ev);
                        if (showRefreshFlag)showRefreshFlag = false;
                        if (!showLoadFlag){
                            showLoadFlag = true;
                            INITIAL_Y = agentY;
                            lastY = agentY;
                        }
                        if (lastY == agentY){
                            break;
                        }
                        float prePercent = mLoadDrawable.getPercent();
                        float newPercent = fixPercent(Math.abs(calculatePercent(INITIAL_Y,
                                agentY, DRAG_MAX_DISTANCE_V, DRAG_RATE)));
                        mLoadDrawable.setPercent(newPercent,true);
                        ((RefreshLoadWrapper) getAdapter()).setLoadHeight(getViewOffset(newPercent));
                        getLayoutManager().offsetChildrenVertical((getViewOffset(prePercent) - getViewOffset(newPercent)));
                        lastY = agentY;
                        return true;
                    }else {
                        if (showLoadFlag)showLoadFlag = false;
                        lastY = agentY;
                        break;
                    }
                }else {
                    showLoadFlag = showRefreshFlag = false;
                    mRefreshDrawable.setPercent(0,false);
                    mLoadDrawable.setPercent(0,true);
                    lastY = agentY;
                    return true;
                }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!showLoadFlag && !showRefreshFlag)break;
                actionUpOrCancel();
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private boolean isRunning(){
        return animator != null && (animator.isRunning() || animator.isStarted());
    }

    private void stop(){
        animator.cancel();
    }

    private int getViewOffset(float percent){
        if (showRefreshFlag){
            return Math.min((int) (percent * (float) mRefreshDrawable.getIntrinsicHeight() * 0.8),mRefreshDrawable.getIntrinsicHeight());
        }
        return Math.min((int) (percent * (float) mLoadDrawable.getIntrinsicHeight() * 0.8),mRefreshDrawable.getIntrinsicHeight());
    }

    private void actionUpOrCancel(){
        if(showLoadFlag && showRefreshFlag){
            throw new IllegalStateException("load state and refresh state should be mutual exclusion!");
        }
        if (showRefreshFlag){
            if (mRefreshDrawable.getPercent() >= AdvancedDrawable.CRITICAL_PERCENT){
                // 回到临界位置
                toCriticalPositionAnimation(mRefreshDrawable.getPercent());
            }else {
                toStartPositionAnimation(mRefreshDrawable.getPercent());
            }
        }else {
            if (mLoadDrawable.getPercent() >= AdvancedDrawable.CRITICAL_PERCENT){
                // 回到临界位置
                toCriticalPositionAnimation(mLoadDrawable.getPercent());
            }else {
                toStartPositionAnimation(mLoadDrawable.getPercent());
            }
        }
    }

    private void toCriticalPositionAnimation(final float start){
        animator = ValueAnimator.ofFloat(start,AdvancedDrawable.CRITICAL_PERCENT);
        animator.setInterpolator(mInterpolator);
        animator.setDuration((long) (MAX_OFFSET_ANIMATION_DURATION * (start - AdvancedDrawable.CRITICAL_PERCENT)));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float percent = (float) animation.getAnimatedValue();
                if (showRefreshFlag){
                    mRefreshDrawable.setPercent(percent,true);
                    ((RefreshLoadWrapper) getAdapter()).setRefreshHeight(getViewOffset(percent));
                }else {
                    float prePercent = mLoadDrawable.getPercent();
                    mLoadDrawable.setPercent(percent,true);
                    ((RefreshLoadWrapper) getAdapter()).setLoadHeight(getViewOffset(percent));
                    getLayoutManager().offsetChildrenVertical((getViewOffset(prePercent) - getViewOffset(percent)));
                }
                if (percent == AdvancedDrawable.CRITICAL_PERCENT){
                    if (showRefreshFlag){
                        gettingData = true;
                        Toast.makeText(getContext(),"refresh",Toast.LENGTH_SHORT).show();
                        if (mDataSource != null){
                            mDataSource.onRefreshing();
                        }
                        mRefreshDrawable.start();
                    }else {
                        gettingData = true;
                        Toast.makeText(getContext(),"load",Toast.LENGTH_SHORT).show();
                        if (mDataSource != null){
                            mDataSource.onLoading();
                        }
                        mLoadDrawable.start();
                    }
                }
            }
        });
        animator.start();
    }

    private void toStartPositionAnimation(final float start){
        animator = ValueAnimator.ofFloat(start,0);
        animator.setInterpolator(mInterpolator);
        animator.setDuration((long) (MAX_OFFSET_ANIMATION_DURATION * start));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float percent = (float) animation.getAnimatedValue();
                if (showRefreshFlag){
                    mRefreshDrawable.setPercent(percent,true);
                    ((RefreshLoadWrapper) getAdapter()).setRefreshHeight(getViewOffset(percent));
                }else {
                    float prePercent = mLoadDrawable.getPercent();
                    mLoadDrawable.setPercent(percent,true);
                    ((RefreshLoadWrapper) getAdapter()).setLoadHeight(getViewOffset(percent));
                    getLayoutManager().offsetChildrenVertical((getViewOffset(prePercent) - getViewOffset(percent)));
                }
                if (percent == 0){
                    showLoadFlag = showRefreshFlag = false;
                }
            }
        });
        animator.start();
    }


    private float fixPercent(float initPercent){
        if (initPercent <= 1){
            return initPercent;
        }else {
            return 1f + (initPercent - 1f) * 0.6f;
        }
    }

    private float calculatePercent(float initialPos,float currentPos,int maxDragDistance,float rate){
        return (currentPos - initialPos) * rate / ((float) maxDragDistance);
    }

    private void calculateInitY(float agentY,int maxDragDistance,float rate,float percent){
        INITIAL_Y = agentY - percent * (float) maxDragDistance / rate;
    }

    private boolean canChildScrollUp() {
        return ViewCompat.canScrollVertically(this, -1);
    }

    private boolean canChildScrollBottom(){
        return !showLoadFlag && !isLastChildShowingCompletely();
    }

    private boolean isLastChildShowingCompletely(){
        return ((getLayoutManager().getPosition(getChildAt(getChildCount() - 2)) == getAdapter().getItemCount() - 2));
    }

    public void setRefreshableAndLoadable(RefreshableAndLoadable dataSource){
        mDataSource = dataSource;
    }

    public void stopRefreshingOrLoading(){
        if (gettingData){
            gettingData = false;
        }
        if (showRefreshFlag){
            mRefreshDrawable.stop();
            toStartPositionAnimation(AdvancedDrawable.CRITICAL_PERCENT);
        }else {
            mLoadDrawable.stop();
            toStartPositionAnimation(AdvancedDrawable.CRITICAL_PERCENT);
        }
    }

    public void setCanRefresh(boolean canRefresh){
        this.canRefresh = canRefresh;
    }

    public void setCanLoad(boolean canLoad){
        this.canLoad = canLoad;
    }
}
