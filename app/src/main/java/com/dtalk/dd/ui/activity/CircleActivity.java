package com.dtalk.dd.ui.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.bigkoo.svprogresshud.SVProgressHUD;
import com.dtalk.dd.R;
import com.dtalk.dd.app.IMApplication;
import com.dtalk.dd.config.SysConstant;
import com.dtalk.dd.http.base.BaseClient;
import com.dtalk.dd.http.base.BaseResponse;
import com.dtalk.dd.http.moment.Moment;
import com.dtalk.dd.http.moment.MomentClient;
import com.dtalk.dd.http.moment.MomentList;
import com.dtalk.dd.imservice.entity.ShortVideoMessage;
import com.dtalk.dd.imservice.event.ShortVideoPubEvent;
import com.dtalk.dd.imservice.manager.IMLoginManager;
import com.dtalk.dd.qiniu.utils.QNUploadManager;
import com.dtalk.dd.ui.adapter.CircleAdapter;
import com.dtalk.dd.ui.base.TTBaseActivity;
import com.dtalk.dd.ui.widget.circle.BaseCircleRenderView;
import com.dtalk.dd.utils.Logger;
import com.dtalk.dd.utils.SandboxUtils;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.PauseOnScrollListener;
import com.yixia.camera.demo.ui.record.MediaRecorderActivity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;
import in.srain.cube.views.ptr.PtrClassicDefaultHeader;
import in.srain.cube.views.ptr.PtrDefaultHandler;
import in.srain.cube.views.ptr.PtrFrameLayout;
import in.srain.cube.views.ptr.PtrHandler;

import static com.yixia.camera.demo.utils.ToastUtils.showToast;

/**
 * Created by Donal on 16/7/29.
 */
public class CircleActivity extends TTBaseActivity implements View.OnClickListener, AbsListView.OnScrollListener, BaseCircleRenderView.OnDeleteCircleListener {
    CircleAdapter adapter;

    @ViewInject(R.id.ptrFrameLayoutShare)
    private PtrFrameLayout ptrFrameLayoutShare;
    private View footer;
    @ViewInject(R.id.ptr_classic_footer_rotate_view_footer_title)
    private TextView ptr_classic_footer_rotate_view_footer_title;
    @ViewInject(R.id.ptr_classic_footer_rotate_view_progressbar)
    private ProgressBar ptr_classic_footer_rotate_view_progressbar;
    private ListView listView;
    String lastId;
    private int lvDataState;
    private SVProgressHUD svProgressHUD;
//    @Override
//    protected void onSaveInstanceState(Bundle savedInstanceState) {
//        savedInstanceState.putInt(STATE_SCORE, listView.getFirstVisiblePosition());
//        savedInstanceState.putSerializable("listdata", (Serializable) adapter.getCircleObjectList());
//
//        super.onSaveInstanceState(savedInstanceState);
//    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        MomentList temp = new MomentList();
        temp.list = new ArrayList<>();
        temp.list.addAll(adapter.getCircleObjectList());
        temp.status = listView.getFirstVisiblePosition();
        SandboxUtils.getInstance().saveObject(IMApplication.getInstance(), temp, "circle");
        super.onDestroy();
        svProgressHUD.dismiss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        svProgressHUD = new SVProgressHUD(this);
        initView();
        MomentList temp = (MomentList) SandboxUtils.getInstance().readObject(IMApplication.getInstance(), "circle");
        if (temp != null) {
            adapter.addItemList(temp.list);
            listView.setSelectionFromTop(temp.status, 0);
            if (!temp.list.isEmpty()) {
                lastId = temp.list.get(temp.list.size() - 1).moment_id;
                lvDataState = SysConstant.LISTVIEW_DATA_MORE;
            } else {
                lvDataState = SysConstant.LISTVIEW_DATA_FULL;
                if (lvDataState == SysConstant.LISTVIEW_DATA_FULL) {
                    ptr_classic_footer_rotate_view_footer_title.setText(R.string.cube_ptr_finish);
                    ptr_classic_footer_rotate_view_progressbar.setVisibility(View.GONE);
                }
            }
        } else {
            fetchMoments("0");
        }
    }

    private void initView() {
        LayoutInflater.from(this).inflate(R.layout.activity_circle, topContentView);
        ViewUtils.inject(this);
        setLeftButton(R.drawable.tt_top_back);
        setLeftText(getResources().getString(R.string.top_left_back));
        setRightButton(R.drawable.circle_camera);
        topLeftBtn.setOnClickListener(this);
        letTitleTxt.setOnClickListener(this);
        topRightBtn.setOnClickListener(this);
        PtrClassicDefaultHeader ptrHeader = new PtrClassicDefaultHeader(this);
        ptrFrameLayoutShare.setDurationToCloseHeader(500);
        ptrFrameLayoutShare.setHeaderView(ptrHeader);
        ptrFrameLayoutShare.addPtrUIHandler(ptrHeader);
        ptrFrameLayoutShare.setPtrHandler(new PtrHandler() {
            @Override
            public boolean checkCanDoRefresh(PtrFrameLayout frame, View content, View header) {
                return PtrDefaultHandler.checkContentCanBePulledDown(ptrFrameLayoutShare, content, header);
            }

            @Override
            public void onRefreshBegin(PtrFrameLayout frame) {
                lastId = "0";
                fetchMoments(lastId);
            }
        });

        listView = (ListView) findViewById(R.id.lvShare);
//        listView.addHeaderView(header);
        footer = LayoutInflater.from(this).inflate(R.layout.cube_ptr_classic_default_footer, null);
        ViewUtils.inject(this, footer);
        listView.addFooterView(footer, null, false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setOnScrollListener(this);

        adapter = new CircleAdapter(this, this);
        listView.setAdapter(adapter);
        listView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });
        lastId = "0";
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        switch (id) {
            case R.id.left_btn:
            case R.id.left_txt:
                finish();
                break;
            case R.id.right_btn:
                new MaterialDialog.Builder(this)
                        .items(R.array.circle_media)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                switch (which) {
                                    case 0:
                                        CircleImagePubActivity.launch(CircleActivity.this);
                                        break;
                                    case 1:
                                        takeShortVideo();
                                        break;
                                }
                            }
                        })
                        .show();
                break;
            default:
                break;
        }
    }

    private void takeShortVideo() {
        Intent intent = new Intent(getApplication(),
                MediaRecorderActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.push_up_in,
                R.anim.push_up_out);
    }

    public void onEventMainThread(ShortVideoPubEvent event) {
        handleSendVideoAttachMessage(event.cover, event.path);
    }

    /**
     * 处理发送小视频
     *
     * @param coverName
     * @param pathName
     */
    private void handleSendVideoAttachMessage(String coverName, final String pathName) {
        if (TextUtils.isEmpty(pathName)) {
            return;
        }
        List<String> list = new ArrayList<>();
        list.add(coverName);
        svProgressHUD.getProgressBar().setProgress(0);
        svProgressHUD.showWithProgress("正在上传小视频", SVProgressHUD.SVProgressHUDMaskType.Black);
        QNUploadManager.getInstance(this).uploadCircleFiles(list, null, new QNUploadManager.OnQNUploadCallback() {
            @Override
            public void uploadCompleted(Map<String, String> uploadedFiles) {
                Logger.d(uploadedFiles.values().toString());
                uploadVideo((String) uploadedFiles.values().toArray()[0], pathName);
            }
        });
    }

    private void uploadVideo(final String videoCover, final String path) {
        svProgressHUD.getProgressBar().setProgress(0);
        QNUploadManager.getInstance(this).uploadCircleVideo(path, svProgressHUD, new QNUploadManager.OnQNUploadCallback() {
            @Override
            public void uploadCompleted(Map<String, String> uploadedFiles) {
                postVideo((String) uploadedFiles.values().toArray()[0], videoCover, path);
            }
        });
    }

    /**
     * @param msg
     */
    public void pushList(Moment msg) {
        adapter.addItem(msg);
    }

    public void pushList(List<Moment> entityList) {
        adapter.addItemList(entityList);
    }

    private void fetchMoments(final String last) {
        MomentClient.fetchMoment((String.valueOf(IMLoginManager.instance().getLoginId())), SandboxUtils.getInstance().get(IMApplication.getInstance(), "token"), last, "10", new BaseClient.ClientCallback() {

            @Override
            public void onSuccess(Object data) {
                MomentList list = (MomentList) data;
                if (last.equals("0")) {
                    adapter.clearAllItem();
                }
                pushList(list.list);
                if (!list.list.isEmpty()) {
                    lastId = list.list.get(list.list.size() - 1).moment_id;
                    lvDataState = SysConstant.LISTVIEW_DATA_MORE;
                } else {
                    lvDataState = SysConstant.LISTVIEW_DATA_FULL;
                    if (lvDataState == SysConstant.LISTVIEW_DATA_FULL) {
                        ptr_classic_footer_rotate_view_footer_title.setText(R.string.cube_ptr_finish);
                        ptr_classic_footer_rotate_view_progressbar.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onPreConnection() {
            }

            @Override
            public void onFailure(String message) {
            }

            @Override
            public void onException(Exception e) {
            }


            @Override
            public void onCloseConnection() {
                if (ptrFrameLayoutShare != null) {
                    ptrFrameLayoutShare.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ptrFrameLayoutShare.refreshComplete();
                        }
                    }, 1000);
                }
            }
        });
    }

    private View.OnTouchListener lvPTROnTouchListener = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
            }
            return false;
        }
    };

    @Override
    public void onScrollStateChanged(AbsListView view, int i) {
        boolean scrollEnd = false;
        try {
            if (view.getPositionForView(footer) == view.getLastVisiblePosition())
                scrollEnd = true;
        } catch (Exception e) {
            scrollEnd = false;
        }
        if (scrollEnd && lvDataState == SysConstant.LISTVIEW_DATA_MORE) {
            lvDataState = SysConstant.LISTVIEW_DATA_LOADING;
            ptr_classic_footer_rotate_view_footer_title.setText(R.string.cube_ptr_refreshing);
            ptr_classic_footer_rotate_view_progressbar.setVisibility(View.VISIBLE);
            fetchMoments(lastId);
        }
    }

    @Override
    public void onScroll(AbsListView absListView, int i, int i1, int i2) {

    }

    private void postVideo(String videoUrl, String videoCover, String localPath) {
        MomentClient.postVideoMoment((String.valueOf(IMLoginManager.instance().getLoginId())), SandboxUtils.getInstance().get(IMApplication.getInstance(), "token"), videoUrl, videoCover, localPath, new BaseClient.ClientCallback() {
            @Override
            public void onPreConnection() {
                svProgressHUD.showWithStatus("发送朋友圈");
            }

            @Override
            public void onCloseConnection() {
                svProgressHUD.dismiss();
            }

            @Override
            public void onSuccess(Object data) {
                BaseResponse response = (BaseResponse) data;
                if (response.getStatus() == 1) {
                    lastId = "0";
                    fetchMoments(lastId);
                } else {
                    svProgressHUD.showErrorWithStatus(response.getMsg());
                }
            }

            @Override
            public void onFailure(String message) {
                svProgressHUD.showErrorWithStatus(message);
            }

            @Override
            public void onException(Exception e) {

            }
        });
    }

    @Override
    public void onDeleteCircle(final Moment moment) {
        new MaterialDialog.Builder(this)
                .title("提示")
                .content("删除该条朋友圈?")
                .positiveText("确定")
                .negativeText("返回")
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        deleteCircle(moment);
                    }
                })
                .show();
    }

    private void deleteCircle(Moment moment) {
        adapter.getCircleObjectList().remove(moment);
        adapter.notifyDataSetChanged();
        MomentClient.deleteMoment((String.valueOf(IMLoginManager.instance().getLoginId())), SandboxUtils.getInstance().get(IMApplication.getInstance(), "token"), moment.moment_id, null);
    }
}
