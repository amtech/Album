package com.album.ui.dialog

import android.content.DialogInterface
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.album.*
import com.album.core.AlbumCore
import com.album.core.scan.AlbumEntity
import com.album.core.ui.AlbumBaseDialogFragment
import com.album.listener.AlbumParentListener
import com.album.ui.fragment.AlbumFragment
import com.album.ui.fragment.PrevFragment
import kotlinx.android.synthetic.main.album_fragment_dialog.*

class AlbumDialogFragment : AlbumBaseDialogFragment(), AlbumParentListener {

    companion object {
        fun newInstance(albumBundle: AlbumBundle, uiBundle: AlbumDialogUiBundle): AlbumDialogFragment {
            val albumDialogFragment = AlbumDialogFragment()
            albumDialogFragment.arguments = Bundle().apply {
                putParcelable(EXTRA_ALBUM_OPTIONS, albumBundle)
                putParcelable(EXTRA_ALBUM_UI_OPTIONS, uiBundle)
            }
            return albumDialogFragment
        }
    }

    override fun viewLayout(): View = View.inflate(mActivity, R.layout.album_fragment_dialog, null)

    override val themeId: Int = R.style.BottomDialog

    private lateinit var albumUiBundle: AlbumDialogUiBundle
    private lateinit var albumBundle: AlbumBundle

    private lateinit var albumFragment: AlbumFragment
    private lateinit var prevFragment: PrevFragment

    override fun onStart() {
        super.onStart()
        val dialog = dialog ?: return
        val window = dialog.window ?: return
        val attributes = window.attributes ?: return
        attributes.width = LinearLayout.LayoutParams.MATCH_PARENT
        attributes.height = LinearLayout.LayoutParams.WRAP_CONTENT
        attributes.gravity = Gravity.BOTTOM
        window.attributes = attributes
    }

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && hasShowPrevFragment()) {
                albumFragment.onDialogResultPreview(prevFragment.isDialogRefreshAlbumUI(albumUiBundle.previewBackRefresh))
                childFragmentManager.beginTransaction().show(albumFragment).remove(prevFragment).commitAllowingStateLoss()
                return@setOnKeyListener true
            }
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        albumUiBundle = bundle.getParcelable(EXTRA_ALBUM_UI_OPTIONS) ?: AlbumDialogUiBundle()
        albumBundle = bundle.getParcelable(EXTRA_ALBUM_OPTIONS) ?: AlbumBundle()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        album_dialog_fragment.layoutParams.height = resources.displayMetrics.heightPixels / 2

        album_dialog_title.setTitle(albumUiBundle.toolbarText)
        album_dialog_title.setTitleTextColor(ContextCompat.getColor(mActivity, albumUiBundle.toolbarTextColor))
        val drawable = ContextCompat.getDrawable(mActivity, albumUiBundle.toolbarIcon)
        drawable?.setColorFilter(ContextCompat.getColor(mActivity, albumUiBundle.toolbarIconColor), PorterDuff.Mode.SRC_ATOP)
        album_dialog_title.navigationIcon = drawable
        album_dialog_title.setBackgroundColor(ContextCompat.getColor(mActivity, albumUiBundle.toolbarBackground))
        if (AlbumCore.hasL()) {
            album_dialog_title.elevation = albumUiBundle.toolbarElevation
        }

        album_dialog_title.setNavigationOnClickListener {
            if (albumFragment.isVisible) {
                dismiss()
                return@setNavigationOnClickListener
            }
            albumFragment.onDialogResultPreview(prevFragment.isDialogRefreshAlbumUI(albumUiBundle.previewFinishRefresh))
            childFragmentManager.beginTransaction().show(albumFragment).remove(prevFragment).commitAllowingStateLoss()
        }

        album_dialog_bottom_view_ok.setOnClickListener {
            if (hasShowPrevFragment()) {
                Toast.makeText(mActivity, "目前在预览阶段", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (albumFragment.getSelectEntity().isEmpty()) {
                Album.instance.albumListener?.onAlbumSelectEmpty()
                return@setOnClickListener
            }
            Album.instance.albumListener?.onAlbumResources(albumFragment.getSelectEntity())
            dismiss()
        }

        album_dialog_bottom_view_preview.setOnClickListener {
            if (albumFragment.getSelectEntity().isEmpty()) {
                Album.instance.albumListener?.onAlbumPreviewEmpty()
                return@setOnClickListener
            }
            if (hasShowPrevFragment()) {
                Toast.makeText(mActivity, "正在预览阶段", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            initPrevFragment(albumFragment.getSelectEntity(), 0, PREVIEW_BUTTON_KEY)
        }

        initAlbumFragment()

    }

    override fun onAlbumItemClick(multiplePreviewList: ArrayList<AlbumEntity>, position: Int, bucketId: String) {
        initPrevFragment(multiplePreviewList, if (TextUtils.isEmpty(bucketId) && !albumBundle.hideCamera) position - 1 else position, bucketId)
    }

    private fun hasShowPrevFragment() = ::prevFragment.isInitialized && prevFragment.isVisible

    private fun initPrevFragment(multiplePreviewList: ArrayList<AlbumEntity>, position: Int, bucketId: String) {
        val bundle = Bundle().apply {
            putParcelableArrayList(TYPE_PREVIEW_KEY, multiplePreviewList)
            putInt(TYPE_PREVIEW_POSITION_KEY, position)
            putString(TYPE_PREVIEW_BUCKET_ID, bucketId)
            putParcelable(EXTRA_ALBUM_OPTIONS, albumBundle)
        }
        if (::prevFragment.isInitialized && !prevFragment.isRemoving) {
            childFragmentManager.beginTransaction().remove(prevFragment).commitAllowingStateLoss()
        }
        prevFragment = PrevFragment.newInstance(bundle)
        childFragmentManager.beginTransaction().apply { add(R.id.album_dialog_fragment, prevFragment, PrevFragment::class.java.simpleName) }.hide(albumFragment).commitAllowingStateLoss()
    }

    private fun initAlbumFragment() {
        val fragment = childFragmentManager.findFragmentByTag(AlbumFragment::class.java.simpleName)
        if (fragment != null) {
            albumFragment = fragment as AlbumFragment
            childFragmentManager.beginTransaction().show(fragment).commitAllowingStateLoss()
        } else {
            albumFragment = AlbumFragment.newInstance(albumBundle)
            childFragmentManager
                    .beginTransaction()
                    .apply { add(R.id.album_dialog_fragment, albumFragment, AlbumFragment::class.java.simpleName) }
                    .commitAllowingStateLoss()
        }
        albumFragment.albumParentListener = this
    }

    override fun onDismiss(dialog: DialogInterface?) {
        albumFragment.disconnectMediaScanner()
        super.onDismiss(dialog)
        Album.instance.albumListener?.onAlbumContainerFinish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Album.destroy()
    }
}
