package com.pda.hcw;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

/**
 * 页面级状态横幅：统一展示 error/warning/info 等明文提示。
 */
public class StatusBannerView extends LinearLayout {
    public enum Type { ERROR, WARNING, INFO }

    private CardView root;
    private View accent;
    private TextView text;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long autoDismissMs = 2000L; // 默认2秒，可由外部覆盖
    private boolean sticky = false; // 粘滞模式：不自动消失
    private boolean closable = false; // 是否显示关闭按钮
    private ImageView closeBtn;
    private Runnable onCloseListener;

    public StatusBannerView(Context context) {
        super(context);
        init(context);
    }

    public StatusBannerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StatusBannerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context ctx) {
        setOrientation(VERTICAL);
        LayoutInflater.from(ctx).inflate(R.layout.widget_status_banner, this, true);
        root = findViewById(R.id.status_banner_root);
        accent = findViewById(R.id.status_banner_accent);
        text = findViewById(R.id.status_banner_text);
        closeBtn = findViewById(R.id.status_banner_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> {
                hide();
                if (onCloseListener != null) try { onCloseListener.run(); } catch (Throwable ignore) {}
            });
        }
    }

    public void show(Type type, CharSequence message) {
        if (root == null) return;
        int accentColor;
        int bgColor;
        switch (type) {
            case ERROR:
                accentColor = getResources().getColor(R.color.error);
                bgColor = getResources().getColor(R.color.error_bg);
                break;
            case WARNING:
                accentColor = getResources().getColor(R.color.warning);
                bgColor = getResources().getColor(R.color.warning_bg);
                break;
            default:
                accentColor = getResources().getColor(R.color.info);
                bgColor = getResources().getColor(R.color.info_bg);
        }
        if (accent != null) accent.setBackgroundColor(accentColor);
        if (text != null) {
            text.setText(message != null ? message : "");
            text.setTextColor(getResources().getColor(R.color.text_secondary));
        }
        root.setCardBackgroundColor(bgColor);
        // 关闭按钮可见性
        if (closeBtn != null) {
            closeBtn.setVisibility(closable ? VISIBLE : GONE);
        }

        animateIn();
        root.setVisibility(VISIBLE);
        setVisibility(VISIBLE);

        // 自动消失（非粘滞）
        handler.removeCallbacksAndMessages(null);
        if (!sticky && autoDismissMs > 0) {
            handler.postDelayed(this::animateOutAndHide, autoDismissMs);
        }
    }

    public void hide() {
        if (root != null) root.setVisibility(GONE);
        setVisibility(GONE);
    }

    public void setAutoDismissMs(long ms) {
        this.autoDismissMs = ms > 0 ? ms : 2000L;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public void setClosable(boolean closable) {
        this.closable = closable;
        if (closeBtn != null) closeBtn.setVisibility(closable ? VISIBLE : GONE);
    }

    public void setOnCloseListener(@Nullable Runnable r) {
        this.onCloseListener = r;
    }

    public void showSticky(Type type, CharSequence message) {
        setSticky(true);
        setClosable(true);
        show(type, message);
    }

    private void animateIn() {
        try {
            setAlpha(0f);
            setTranslationY(-getHeight());
            animate().alpha(1f).translationY(0).setDuration(160).start();
        } catch (Throwable ignore) {}
    }

    private void animateOutAndHide() {
        try {
            animate().alpha(0f).translationY(-getHeight()).setDuration(150)
                    .withEndAction(this::hide).start();
        } catch (Throwable ignore) { hide(); }
    }
}
