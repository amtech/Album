package com.album.ui.fragment

import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.album.*
import com.album.core.AlbumCamera.CUSTOMIZE_CAMERA_REQUEST_CODE
import com.album.core.AlbumCamera.CUSTOMIZE_CAMERA_RESULT_PATH_KEY
import com.album.core.AlbumCamera.OPEN_CAMERA_ERROR
import com.album.core.AlbumCamera.OPEN_CAMERA_REQUEST_CODE
import com.album.core.AlbumCamera.getCameraFile
import com.album.core.AlbumCamera.openCamera
import com.album.core.AlbumCore.imageViewWidthAndHeight
import com.album.core.AlbumCore.orEmpty
import com.album.core.AlbumFile.fileExists
import com.album.core.AlbumFile.pathToFile
import com.album.core.AlbumPermission.TYPE_PERMISSIONS_ALBUM
import com.album.core.AlbumPermission.TYPE_PERMISSIONS_CAMERA
import com.album.core.AlbumPermission.permissionCamera
import com.album.core.AlbumPermission.permissionStorage
import com.album.core.AlbumView.hide
import com.album.core.AlbumView.show
import com.album.core.scan.AlbumEntity
import com.album.core.scan.AlbumScan.VIDEO
import com.album.core.scan.AlbumScanImpl
import com.album.core.scan.AlbumSingleMediaScanner
import com.album.core.scan.FinderEntity
import com.album.core.ui.AlbumBaseFragment
import com.album.core.view.AlbumView
import com.album.listener.AlbumMethodFragmentViewListener
import com.album.listener.AlbumParentListener
import com.album.ui.adapter.AlbumAdapter
import com.album.widget.LoadMoreRecyclerView
import com.album.widget.SimpleGridDivider
import com.yalantis.ucrop.UCrop
import kotlinx.android.synthetic.main.album_fragment_album.*
import java.io.File

/**
 * by y on 14/08/2017.
 */

class AlbumFragment : AlbumBaseFragment(),
        AlbumView,
        AlbumMethodFragmentViewListener,
        AlbumAdapter.OnItemClickListener,
        AlbumSingleMediaScanner.SingleScannerListener,
        LoadMoreRecyclerView.LoadMoreListener {

    companion object {
        /**
         * 获取图库fragment
         */
        fun newInstance(albumBundle: AlbumBundle) = AlbumFragment().apply {
            arguments = Bundle().apply { putParcelable(EXTRA_ALBUM_OPTIONS, albumBundle) }
        }
    }

    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var albumScan: AlbumScanImpl
    private lateinit var albumBundle: AlbumBundle

    /**
     * UI使用回调,可能会用到
     */
    var albumParentListener: AlbumParentListener? = null

    /**
     * 目录数据
     */
    private lateinit var finderEntityList: ArrayList<FinderEntity>

    /**
     * 拍照保存的图片Uri
     */
    private lateinit var imagePath: Uri

    /**
     * 拍照之后刷新数据库
     */
    private var singleMediaScanner: AlbumSingleMediaScanner? = null

    /**
     *当前 bucketId
     */
    var bucketId: String = ""

    /**
     * 当前页数
     */
    private var page = 0

    /**
     *当前的文件夹名称
     */
    var finderName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        albumBundle = bundle.getParcelable(EXTRA_ALBUM_OPTIONS) ?: AlbumBundle()
        if (savedInstanceState == null) {
            imagePath = Uri.fromFile(mActivity.getCameraFile(albumBundle.cameraPath, albumBundle.scanType == VIDEO))
            return
        }
        bucketId = savedInstanceState.getString(TYPE_ALBUM_STATE_BUCKET_ID, "")
        finderName = savedInstanceState.getString(TYPE_ALBUM_STATE_FINDER_NAME, "")
        imagePath = savedInstanceState.getParcelable(TYPE_ALBUM_STATE_IMAGE_PATH) ?: Uri.EMPTY
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(TYPE_ALBUM_STATE_SELECT, getSelectEntity())
        outState.putString(TYPE_ALBUM_STATE_BUCKET_ID, bucketId)
        outState.putString(TYPE_ALBUM_STATE_FINDER_NAME, finderName)
        outState.putParcelable(TYPE_ALBUM_STATE_IMAGE_PATH, imagePath)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        album_content_view.setBackgroundColor(ContextCompat.getColor(mActivity, albumBundle.rootViewBackground))
        val drawable = ContextCompat.getDrawable(mActivity, albumBundle.photoEmptyDrawable)
        drawable?.setColorFilter(ContextCompat.getColor(mActivity, albumBundle.photoEmptyDrawableColor), PorterDuff.Mode.SRC_ATOP)
        album_empty.setImageDrawable(drawable)
        album_empty.setOnClickListener { v ->
            val emptyClickListener = Album.instance.emptyClickListener
            if (emptyClickListener != null) {
                if (emptyClickListener.click(v)) {
                    startCamera()
                }
            }
        }

        finderEntityList = ArrayList()
        albumScan = AlbumScanImpl.newInstance(this, albumBundle.scanType, albumBundle.scanCount, albumBundle.allName, albumBundle.sdName, albumBundle.filterImg)
        album_recyclerView.setHasFixedSize(true)
        val gridLayoutManager = GridLayoutManager(mActivity, albumBundle.spanCount)
        album_recyclerView.layoutManager = gridLayoutManager
        album_recyclerView.setLoadingListener(this)
        album_recyclerView.addItemDecoration(SimpleGridDivider(albumBundle.dividerWidth))
        albumAdapter = AlbumAdapter(mActivity.imageViewWidthAndHeight(albumBundle.spanCount), albumBundle, this)

        val selectList = savedInstanceState?.getParcelableArrayList<AlbumEntity>(TYPE_ALBUM_STATE_SELECT)
                ?: ArrayList()
        if (!selectList.isEmpty()) {
            albumAdapter.multipleList = selectList
        }

        album_recyclerView.adapter = albumAdapter
        onScanAlbum(bucketId, isFinder = false, result = false)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_CANCELED -> when (requestCode) {
                TYPE_PREVIEW_REQUEST_CODE -> onResultPreview(data?.extras.orEmpty())
                UCrop.REQUEST_CROP -> Album.instance.albumListener?.onAlbumCropCanceled()
                OPEN_CAMERA_REQUEST_CODE -> Album.instance.albumListener?.onAlbumCameraCanceled()
            }
            UCrop.RESULT_ERROR -> {
                Album.instance.albumListener?.onAlbumUCropError(UCrop.getError(data.orEmpty()))
                if (albumBundle.cropErrorFinish) {
                    mActivity.finish()
                }
            }
            Activity.RESULT_OK -> when (requestCode) {
                CUSTOMIZE_CAMERA_REQUEST_CODE -> {
                    val extras = data?.extras
                    if (extras != null) {
                        val customizePath = extras.getString(CUSTOMIZE_CAMERA_RESULT_PATH_KEY)
                        if (!TextUtils.isEmpty(customizePath)) {
                            imagePath = Uri.fromFile(File(customizePath))
                            refreshMedia(TYPE_RESULT_CAMERA, imagePath.path.orEmpty())
                            if (albumBundle.cameraCrop) {
                                openUCrop(imagePath.path.orEmpty())
                            }
                        }
                    }
                }
                OPEN_CAMERA_REQUEST_CODE -> {
                    refreshMedia(TYPE_RESULT_CAMERA, imagePath.path.orEmpty())
                    if (albumBundle.cameraCrop) {
                        openUCrop(imagePath.path.orEmpty())
                    }
                }
                UCrop.REQUEST_CROP -> {
                    if (data == null) {
                        Album.instance.albumListener?.onAlbumUCropError(null)
                        return
                    }
                    val path = data.extras?.getParcelable<Uri>(UCrop.EXTRA_OUTPUT_URI)?.path.orEmpty()
                    Album.instance.albumListener?.onAlbumUCropResources(path.pathToFile())
                    refreshMedia(TYPE_RESULT_CROP, path)
                    if (albumBundle.cropFinish) {
                        mActivity.finish()
                    }
                }
                TYPE_PREVIEW_REQUEST_CODE -> onResultPreview(data?.extras.orEmpty())
            }
        }
    }


    override fun scanSuccess(albumEntityList: ArrayList<AlbumEntity>) {
        album_empty.hide()
        if (TextUtils.isEmpty(bucketId) && !albumBundle.hideCamera && page == 0 && !albumEntityList.isEmpty()) {
            albumEntityList.add(0, AlbumEntity(path = CAMERA))
        }
        albumAdapter.addAll(albumEntityList)
        if (page == 0 && !albumBundle.radio && getSelectEntity().isEmpty()) {
            val selectEntity = Album.instance.initList
            if (selectEntity != null && !selectEntity.isEmpty() && !albumEntityList.isEmpty()) {
                albumScan.mergeEntity(albumEntityList, selectEntity)
                albumAdapter.multipleList = selectEntity
            }
        }
        ++page
    }

    override fun scanFinderSuccess(list: ArrayList<FinderEntity>) {
        finderEntityList.clear()
        finderEntityList.addAll(list)
    }

    override fun onAlbumNoMore() {
        Album.instance.albumListener?.onAlbumNoMore()
    }

    override fun onAlbumEmpty() {
        album_empty.show()
        Album.instance.albumListener?.onAlbumEmpty()
    }

    override fun resultSuccess(albumEntity: AlbumEntity?) {
        if (albumEntity == null) {
            Album.instance.albumListener?.onAlbumResultCameraError()
        } else {
            albumAdapter.albumList.add(1, albumEntity)
            albumAdapter.notifyDataSetChanged()
        }
    }

    override fun onItemClick(view: View, position: Int, albumEntity: AlbumEntity) {
        if (position == 0 && TextUtils.equals(albumEntity.path, CAMERA)) {
            if (permissionCamera()) {
                startCamera()
            }
            return
        }
        if (!albumEntity.path.fileExists()) {
            Album.instance.albumListener?.onAlbumFileNotExist()
            return
        }
        if (albumBundle.scanType == VIDEO) {
            try {
                val openVideo = Intent(Intent.ACTION_VIEW)
                openVideo.setDataAndType(Uri.parse(albumEntity.path), "video/*")
                startActivity(openVideo)
            } catch (e: Exception) {
                Album.instance.albumListener?.onVideoPlayError()
            }
            return
        }
        if (albumBundle.radio) {
            if (albumBundle.crop) {
                openUCrop(albumEntity.path)
            } else {
                val list = ArrayList<AlbumEntity>()
                list.add(albumEntity)
                Album.instance.albumListener?.onAlbumResources(list)
                if (albumBundle.selectImageFinish) {
                    mActivity.finish()
                }
            }
            return
        }
        if (albumBundle.noPreview) {
            return
        }
        albumParentListener?.onAlbumItemClick(getSelectEntity(), position, bucketId)
    }

    /**
     * 扫描图库
     * [bucketId] 如果为空则扫描整个
     * [isFinder] 是否是点击相册名称扫描
     * [result] 是否是拍照之后扫描
     */
    override fun onScanAlbum(bucketId: String, isFinder: Boolean, result: Boolean) {
        if (isFinder) {
            page = 0
            albumAdapter.removeAll()
        }
        this.bucketId = bucketId
        if (!permissionStorage()) {
            return
        }
        // 如果 albumList 为空则是没有图片拍照的第一张图片,这时直接扫描整个图库即可
        if (result && !albumAdapter.albumList.isEmpty()) {
            albumScan.resultScan(imagePath.path.orEmpty())
            return
        }
        albumScan.scanAll(bucketId, page)
    }

    override fun onScanCropAlbum(path: String) {
        albumScan.resultScan(path)
    }

    override fun onScanStart() {}

    override fun onScanCompleted(type: Int, path: String) {
        mActivity.runOnUiThread {
            if (type == TYPE_RESULT_CROP) {
                onScanCropAlbum(path)
            } else {
                onScanAlbum(bucketId, isFinder = false, result = true)
            }
        }
    }

    override fun disconnectMediaScanner() {
        if (singleMediaScanner != null) {
            singleMediaScanner?.disconnect()
            singleMediaScanner = null
        }
    }

    /**
     * 启动相机
     * [imagePath]每次都赋值,确保拍照时不会重复
     */
    override fun startCamera() {
        val albumCameraListener = Album.instance.customCameraListener
        if (albumCameraListener != null) {
            albumCameraListener.startCamera(this)
            return
        }
        imagePath = Uri.fromFile(mActivity.getCameraFile(albumBundle.cameraPath, albumBundle.scanType == VIDEO))
        val i = openCamera(imagePath, albumBundle.scanType == VIDEO)
        if (i == OPEN_CAMERA_ERROR) {
            Album.instance.albumListener?.onAlbumOpenCameraError()
        }
    }

    override fun refreshMedia(type: Int, path: String) {
        disconnectMediaScanner()
        singleMediaScanner = AlbumSingleMediaScanner(mActivity, path, this@AlbumFragment, type)
    }

    override fun getSelectEntity(): ArrayList<AlbumEntity> = albumAdapter.multipleList

    /**
     * 预览时多选的数据,可能为空,[multipleSelect]结果类似
     */
    override fun selectPreview(): ArrayList<AlbumEntity> {
        if (getSelectEntity().isEmpty()) {
            Album.instance.albumListener?.onAlbumPreviewEmpty()
            return ArrayList()
        }
        return getSelectEntity()
    }

    /**
     * 获取多选时的数据,可能为空
     */
    override fun multipleSelect() {
        if (getSelectEntity().isEmpty()) {
            Album.instance.albumListener?.onAlbumSelectEmpty()
            return
        }
        Album.instance.albumListener?.onAlbumResources(getSelectEntity())
        if (albumBundle.selectImageFinish) {
            mActivity.finish()
        }
    }

    override fun openUCrop(path: String) {
        UCrop.of(Uri.fromFile(File(path)), Uri.fromFile(mActivity.getCameraFile(albumBundle.uCropPath, albumBundle.scanType == VIDEO)))
                .withOptions(Album.instance.options ?: UCrop.Options())
                .start(mActivity, this)
    }

    /**
     * 预览页返回到当前页时需要刷新的数据
     * [TYPE_PREVIEW_KEY] 更新的已选择数据
     * [TYPE_PREVIEW_REFRESH_UI] 是否刷新数据
     * [TYPE_PREVIEW_SELECT_OK_FINISH] 是否销毁依赖的Activity
     */
    override fun onResultPreview(bundle: Bundle) {
        val previewAlbumEntity = bundle.getParcelableArrayList<AlbumEntity>(TYPE_PREVIEW_KEY)
        val isRefreshUI = bundle.getBoolean(TYPE_PREVIEW_REFRESH_UI, true)
        val isFinish = bundle.getBoolean(TYPE_PREVIEW_SELECT_OK_FINISH, false)
        if (isFinish) {
            mActivity.finish()
            return
        }
        if (!isRefreshUI || previewAlbumEntity == null || getSelectEntity() == previewAlbumEntity) {
            return
        }
        albumScan.mergeEntity(albumAdapter.albumList, previewAlbumEntity)
        albumAdapter.multipleList = previewAlbumEntity
    }

    /**
     * 预览页返回到当前页时需要刷新的数据
     * [TYPE_PREVIEW_KEY] 更新的已选择数据
     * [TYPE_PREVIEW_REFRESH_UI] 是否刷新数据
     */
    override fun onDialogResultPreview(bundle: Bundle) {
        val previewAlbumEntity = bundle.getParcelableArrayList<AlbumEntity>(TYPE_PREVIEW_KEY)
        val isRefreshUI = bundle.getBoolean(TYPE_PREVIEW_REFRESH_UI, true)
        if (!isRefreshUI || previewAlbumEntity == null) {
            return
        }
        albumScan.mergeEntity(albumAdapter.albumList, previewAlbumEntity)
        albumAdapter.multipleList = previewAlbumEntity
    }

    override fun onLoadMore() {
        if (permissionStorage()) {
            albumScan.scanAll(bucketId, page)
        }
    }

    override fun permissionsGranted(type: Int) {
        when (type) {
            TYPE_PERMISSIONS_ALBUM -> onScanAlbum(bucketId, isFinder = false, result = false)
            TYPE_PERMISSIONS_CAMERA -> startCamera()
        }
    }

    override fun permissionsDenied(type: Int) {
        Album.instance.albumListener?.onAlbumPermissionsDenied(type)
        if (albumBundle.permissionsDeniedFinish) {
            mActivity.finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disconnectMediaScanner()
    }

    override fun refreshUI() {
        albumAdapter.notifyDataSetChanged()
    }

    override fun showProgress() = album_progress.show()

    override fun hideProgress() = album_progress.hide()

    override fun getFinderEntity(): List<FinderEntity> = finderEntityList

    override fun getAlbumActivity(): FragmentActivity = mActivity

    override fun getPage(): Int = page

    override val layoutId: Int = R.layout.album_fragment_album
}

