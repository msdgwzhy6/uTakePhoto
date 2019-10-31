package com.sl.utakephoto_lib.manager;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;


import com.sl.utakephoto_lib.compress.CompressConfig;
import com.sl.utakephoto_lib.compress.CompressImage;
import com.sl.utakephoto_lib.compress.CompressImageImpl;
import com.sl.utakephoto_lib.crop.CropActivity;
import com.sl.utakephoto_lib.crop.CropExtras;
import com.sl.utakephoto_lib.crop.CropOptions;
import com.sl.utakephoto_lib.exception.TakeException;
import com.sl.utakephoto_lib.utils.ImgUtil;
import com.sl.utakephoto_lib.utils.IntentUtils;
import com.sl.utakephoto_lib.utils.PermissionUtils;
import com.sl.utakephoto_lib.utils.TUriUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import static android.app.Activity.RESULT_OK;
import static com.sl.utakephoto_lib.utils.ImgUtil.JPEG_MIME_TYPE;
import static com.sl.utakephoto_lib.utils.ImgUtil.computeSize;
import static com.sl.utakephoto_lib.utils.ImgUtil.extSuffix;
import static com.sl.utakephoto_lib.utils.ImgUtil.rotatingImage;
import static com.sl.utakephoto_lib.utils.TConstant.*;

/**
 * author : Sl
 * createDate   : 2019-10-1516:48
 * desc   :
 */
public class TakePhotoManager implements LifecycleListener {
    /**
     * 拍照ResultCode
     */
    private static final int TAKE_PHOTO_RESULT = 1 << 2;
    /**
     * 选择ResultCode
     */
    private static final int DIRECTORY_PICTURES_RESULT = 1 << 3;
    /**
     * 裁剪ResultCode
     */
    private static final int PHOTO_WITCH_CROP_RESULT = 1 << 5;
    /**
     * 类型拍照
     */
    private static final int TYPE_TAKE_PHOTO = 1 << 7;
    /**
     * 类型从相册选择
     */
    private static final int TYPE_SELECT_IMAGE = 1 << 8;
    /**
     * 类型
     */
    private int takeType;
    /**
     * 请求权限requestCode
     */
    private static final int PERMISSION_REQUEST_CODE = 1 << 9;

    private UTakePhoto UTakePhoto;
    private final Lifecycle lifecycle;
    private Context mContext;
    private Intent intent;
    private CropOptions cropOptions;
    private String relativePath;
    private CompressConfig compressConfig;
    private ITakePhotoResult takePhotoResult;

    private Uri outPutUri;
    private Uri tempUri;
    private boolean isInit;
    public static final SparseArray<String> ERROR_ARRAY = new SparseArray<>();
    private static final String[] PERMISSION_CAMERAS = new String[]{Manifest.permission.CAMERA, "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private static final String[] PERMISSION_STORAGE = new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};

    static {

        ERROR_ARRAY.put(TYPE_NOT_IMAGE, "不是图片类型");
        ERROR_ARRAY.put(TYPE_WRITE_FAIL, "保存失败");
        ERROR_ARRAY.put(TYPE_URI_NULL, "Uri为null");
        ERROR_ARRAY.put(TYPE_URI_PARSE_FAIL, "Uri解析错误");
        ERROR_ARRAY.put(TYPE_NO_MATCH_PICK_INTENT, "没有找到选择照片的Intent");
        ERROR_ARRAY.put(TYPE_NO_MATCH_CROP_INTENT, "没有找到裁剪照片的Intent");
        ERROR_ARRAY.put(TYPE_NO_CAMERA, "没有找到拍照的Intent");
        ERROR_ARRAY.put(TYPE_NO_FIND, "选择的文件没有找到");
    }


    public TakePhotoManager(
            @NonNull UTakePhoto UTakePhoto,
            @NonNull Lifecycle lifecycle,
            @NonNull Context context) {
        this.UTakePhoto = UTakePhoto;
        this.lifecycle = lifecycle;
        this.mContext = context;
        lifecycle.addListener(this);
        UTakePhoto.registerRequestManager(this);
    }

    /**
     * 默认储存在getExternalFilesDir/Pictures目录下
     *
     * @return
     */
    public TakePhotoManager openCamera() {
        return openCamera(null, null, null);
    }

    /**
     * 打开系统相机，输出路径自定义
     *
     * @param outPutUri 拍照路径
     * @return
     */
    public TakePhotoManager openCamera(Uri outPutUri) {
        return openCamera(outPutUri, null, null);
    }

    /**
     * 打开系统相机，输出路径自定义
     * 在androidQ上建议采用这个方法，因为如果采用outPutUri的方式，会在mediaStore创建一个空的img
     *
     * @param relativePath androidQ上清单文件中android:requestLegacyExternalStorage="true"
     *                     则relativePath 必须以 Pictures/DCIM 为跟路径；
     *                     Q以下默认根路径是Environment.getExternalStorageDirectory()
     * @return
     */
    public TakePhotoManager openCamera(String relativePath) {
        return openCamera(null, relativePath, null);
    }

    private TakePhotoManager openCamera(Uri outPutUri, Intent intent) {
        return openCamera(outPutUri, null, intent);
    }

    private TakePhotoManager openCamera(String relativePath, Intent intent) {
        return openCamera(null, relativePath, intent);
    }

    /**
     * @param outPutUri    输入路径
     * @param relativePath androidQ设置MediaStore.Images.Media.RELATIVE_PATH
     * @param intent       自定义Intent的时候，outPutUri为输出路径，成功需要返回setResult(RESULT_OK)
     * @return
     */
    private TakePhotoManager openCamera(Uri outPutUri, String relativePath, Intent intent) {
        takeType = TYPE_TAKE_PHOTO;
        this.outPutUri = outPutUri;
        this.intent = intent;
        this.relativePath = relativePath;
        return this;
    }

    /**
     * 打开相册
     *
     * @return
     */
    public TakePhotoManager openAlbum() {
        return openAlbum(null);
    }

    /**
     * 打开指定相册
     *
     * @param intent 通过Intent跳转的时候，需要返回setResult(RESULT_OK,Intent.setData(Uri)))
     * @return this
     */
    public TakePhotoManager openAlbum(Intent intent) {
        takeType = TYPE_SELECT_IMAGE;
//
        this.intent = intent;
        return this;
    }

    public TakePhotoManager setCrop(CropOptions cropOptions) {
        this.cropOptions = cropOptions;
        return this;
    }

    public TakePhotoManager setCompressConfig(CompressConfig compressConfig) {
        this.compressConfig = compressConfig;
        return this;
    }


    public void create(ITakePhotoResult takePhotoResult) {
        this.takePhotoResult = takePhotoResult;
        if (!isInit) {
            return;
        }
        checkPermission();
    }


    private void checkPermission() {
        if (takePhotoResult == null) {
            return;
        }
        if (takeType == 0) {
            takePhotoResult.takeFailure(new TakeException(TYPE_OTHER, "You have to make sure you call openCamera or openAlbum"));
            return;
        }
        if (UTakePhoto.getSupportFragment() != null) {
            supportFragmentPermissionCheck(UTakePhoto.getSupportFragment());
        } else if (UTakePhoto.getFragment() != null) {
            fragmentPermissionCheck(UTakePhoto.getFragment());
        }

    }

    private void supportFragmentPermissionCheck(Fragment fragment) {
        if (PermissionUtils.hasSelfPermissions(fragment.getContext(), takeType == TYPE_TAKE_PHOTO ? PERMISSION_CAMERAS : PERMISSION_STORAGE)) {
            permissionGranted();
        } else {
            fragment.requestPermissions(takeType == TYPE_TAKE_PHOTO ? PERMISSION_CAMERAS : PERMISSION_STORAGE, PERMISSION_REQUEST_CODE);
        }
    }

    private void fragmentPermissionCheck(android.app.Fragment fragment) {
        if (PermissionUtils.hasSelfPermissions(fragment.getActivity(), takeType == TYPE_TAKE_PHOTO ? PERMISSION_CAMERAS : PERMISSION_STORAGE)) {
            permissionGranted();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                fragment.requestPermissions(takeType == TYPE_TAKE_PHOTO ? PERMISSION_CAMERAS : PERMISSION_STORAGE, PERMISSION_REQUEST_CODE);
            } else {
                if (takePhotoResult != null) {
                    //"请检查权限是否在manifest里注册：" +
                    takePhotoResult.takeFailure(new TakeException(TYPE_NO_PERMISSION, (takeType == TYPE_TAKE_PHOTO ? Arrays.toString(PERMISSION_CAMERAS) : Arrays.toString(PERMISSION_STORAGE))));
                }
            }
        }
    }


    @Override
    public void onCreate() {
        isInit = true;
        checkPermission();
    }

    @Override
    public void onDestroy() {
        lifecycle.removeListener(this);
        UTakePhoto.unregisterRequestManager(this);
        takePhotoResult = null;
        isInit = false;
        UTakePhoto.onDestroy();
        ERROR_ARRAY.clear();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TAKE_PHOTO_RESULT) {
            if (resultCode == RESULT_OK) {
                if (cropOptions != null) {
                    crop(outPutUri);
                }
                //拍完照 如果设置的是相对路径，需要把图片储存在这个路径下
                if (relativePath != null && relativePath.length() != 0) {
                    ChangeUriTask changeUriTask = new ChangeUriTask();
                    changeUriTask.execute(outPutUri);
                }


            } else {
                takeCancel();
            }
        } else if (requestCode == DIRECTORY_PICTURES_RESULT) {
            if (resultCode == RESULT_OK) {
                if (cropOptions != null) {
                    crop(data.getData());
                } else {
                    handleResult(data.getData());
                }
            } else {
                takeCancel();
            }
        } else if (requestCode == PHOTO_WITCH_CROP_RESULT) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    handleResult(tempUri);
                }
            } else {
                takeCancel();
            }
        }

    }

    private class ChangeUriTask extends AsyncTask<Uri, Void, Uri> {

        @Override
        protected Uri doInBackground(Uri... params) {
            return checkTakePhotoPath(params[0]);
        }

        @Override
        protected void onPostExecute(Uri uri) {
            if (takePhotoResult != null) {
                takePhotoResult.takeSuccess(Collections.singletonList(uri));
            }
        }
    }

    private Uri checkTakePhotoPath(Uri outPutUri) {
        OutputStream outputStream = null;
        InputStream inputStream = null;
        FileOutputStream fos = null;

        try {
            inputStream = mContext.getContentResolver().openInputStream(outPutUri);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.MIME_TYPE, ImgUtil.getMimeType(outPutUri));
                String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
                values.put(MediaStore.Images.Media.DISPLAY_NAME, timeStamp + extSuffix(outPutUri));
                values.put(MediaStore.Images.Media.RELATIVE_PATH, relativePath);
                String status = Environment.getExternalStorageState();
                ContentResolver contentResolver = mContext.getContentResolver();
                Uri insert;
                // 判断是否有SD卡,优先使用SD卡存储,当没有SD卡时使用手机存储
                if (Environment.MEDIA_MOUNTED.equals(status)) {
                    insert = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                } else {
                    insert = contentResolver.insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, values);
                }
                if (insert != null) {
                    outputStream = contentResolver.openOutputStream(insert);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = computeSize(inputStream);
                    Bitmap tagBitmap = BitmapFactory.decodeStream(
                            mContext.getContentResolver().openInputStream(outPutUri), null, options);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    if (JPEG_MIME_TYPE(outPutUri)) {
                        tagBitmap = rotatingImage(tagBitmap, ImgUtil.getMetadataRotation(mContext, outPutUri));
                    }
                    tagBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                    tagBitmap.recycle();
                    if (outputStream != null) {
                        outputStream.write(stream.toByteArray());
                    }
                    Log.d(TAG, "原图路径 :" + insert);

                }

                return insert;

            } else {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = computeSize(inputStream);
                Bitmap tagBitmap = BitmapFactory.decodeStream(
                        mContext.getContentResolver().openInputStream(outPutUri), null, options);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                if (JPEG_MIME_TYPE(outPutUri)) {
                    tagBitmap = rotatingImage(tagBitmap, ImgUtil.getMetadataRotation(mContext, outPutUri));
                }
                tagBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                tagBitmap.recycle();

                String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
                File outputFile = new File(Environment.getExternalStorageDirectory(),
                        relativePath + "/" + timeStamp + extSuffix(outPutUri));
                if (!outputFile.getParentFile().exists()) outputFile.getParentFile().mkdirs();
                Log.d(TAG, "原图路径 :" + outputFile.getPath());
                fos = new FileOutputStream(outputFile);
                fos.write(stream.toByteArray());
                Uri uri = Uri.fromFile(outputFile);
                mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
                return uri;
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return outPutUri;
    }

    private void handleResult(final Uri outPutUri) {
        if (compressConfig == null) {
            if (takePhotoResult != null) {
                takePhotoResult.takeSuccess(Collections.singletonList(outPutUri));
            }
        } else {
            CompressImageImpl.of(mContext, compressConfig, Collections.singletonList(outPutUri), new CompressImage.CompressListener() {
                @Override
                public void onStart() {

                }

                @Override
                public void onSuccess(Uri images) {
                    if (takePhotoResult != null) {
                        Log.d(TAG, "压缩成功 uri：" + images);
                        takePhotoResult.takeSuccess(Collections.singletonList(images));
                    }
                }

                @Override
                public void onError(Throwable obj) {
                    obj.printStackTrace();
                    Log.d(TAG, "压缩失败，返回原图");
                    if (takePhotoResult != null) {
                        takePhotoResult.takeSuccess(Collections.singletonList(outPutUri));
                    }
                }
            }).compress();
        }


    }

    private void takeCancel() {
        if (takePhotoResult != null) {
            takePhotoResult.takeCancel();
        }
        Log.d(TAG, "操作取消");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            ArrayList<String> deniedList = new ArrayList<>();
            ArrayList<String> neverAskAgainList = new ArrayList<>();
            for (int i = 0, j = permissions.length; i < j; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {

                    if (UTakePhoto.getSupportFragment() != null) {
                        if (!PermissionUtils.shouldShowRequestPermissionRationale(UTakePhoto.getSupportFragment(), permissions[i])) {
                            neverAskAgainList.add(permissions[i]);
                        } else {
                            deniedList.add(permissions[i]);
                        }
                    } else if (UTakePhoto.getFragment() != null) {
                        if (!PermissionUtils.shouldShowRequestPermissionRationale(UTakePhoto.getFragment(), permissions[i])) {
                            neverAskAgainList.add(permissions[i]);
                        } else {
                            deniedList.add(permissions[i]);
                        }
                    }
                }
            }
            if (deniedList.isEmpty() && neverAskAgainList.isEmpty()) {
                permissionGranted();
            } else {
                if (!deniedList.isEmpty()) {
                    permissionDenied(deniedList);
                }
                if (!neverAskAgainList.isEmpty()) {
                    permissionNeverAskAgain(neverAskAgainList);

                }
            }
        }
    }


    private void permissionGranted() {
        if (takeType == TYPE_TAKE_PHOTO) {
            try {
                //TODO 在androidQ上 如果outPutUri是MediaStore创建的Uri，图片未保存的时候成功的时候，会留下一个空的img
                this.outPutUri = TUriUtils.checkUri(mContext, outPutUri, extSuffix(outPutUri));
                this.intent = intent == null ? IntentUtils.getCaptureIntent(this.outPutUri) : intent;
            } catch (TakeException e) {
                e.printStackTrace();
                if (takePhotoResult != null) {
                    takePhotoResult.takeFailure(e);
                }
                return;
            }
        } else {
            this.intent = intent == null ? IntentUtils.getPickIntentWithGallery() : intent;
        }

        if (IntentUtils.intentAvailable(mContext, intent)) {
            startActivityForResult(intent, takeType == TYPE_TAKE_PHOTO ? TAKE_PHOTO_RESULT : DIRECTORY_PICTURES_RESULT);
        } else {
            if (takePhotoResult != null) {
                takePhotoResult.takeFailure(new TakeException(takeType == TYPE_TAKE_PHOTO ? TYPE_NO_CAMERA : TYPE_NO_MATCH_PICK_INTENT));
            }
        }
    }

    private void permissionDenied(ArrayList<String> permissions) {
        if (takePhotoResult != null) {
            //拒绝的权限
            takePhotoResult.takeFailure(new TakeException(TYPE_DENIED_PERMISSION, permissions.toString()));
        }
    }

    private void permissionNeverAskAgain(ArrayList<String> permissions) {
        if (takePhotoResult != null) {
            //"以下权限不再询问，请去设置里开启："
            takePhotoResult.takeFailure(new TakeException(TYPE_NEVER_ASK_PERMISSION, permissions.toString()));
        }
    }


    private void startActivityForResult(Intent intent, int requestCode) {
        if (UTakePhoto.getSupportFragment() != null) {
            (UTakePhoto.getSupportFragment()).startActivityForResult(intent, requestCode);
        } else if (UTakePhoto.getFragment() != null) {
            (UTakePhoto.getFragment()).startActivityForResult(intent, requestCode);
        }
    }

    private void startCropActivityForResult(Intent intent, int requestCode) {
        if (UTakePhoto.getSupportFragment() != null) {
            intent.setClass(UTakePhoto.getSupportFragment().getContext(), CropActivity.class);
            UTakePhoto.getSupportFragment().startActivityForResult(intent, requestCode);
        } else if (UTakePhoto.getFragment() != null) {
            intent.setClass(UTakePhoto.getFragment().getActivity(), CropActivity.class);
            UTakePhoto.getFragment().startActivityForResult(intent, requestCode);
        }
    }


    private void crop(Uri takePhotoUri) {
        tempUri = TUriUtils.getTempSchemeFileUri(mContext);
        Log.d(TAG, "tempUri :" + tempUri);
        if (cropOptions.isUseOwnCrop()) {
            Intent cropIntent = new Intent();
            cropIntent.setData(takePhotoUri);
            cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempUri);
            if (cropOptions.getAspectX() * cropOptions.getAspectY() > 0) {
                cropIntent.putExtra(CropExtras.KEY_ASPECT_X, cropOptions.getAspectX());
                cropIntent.putExtra(CropExtras.KEY_ASPECT_Y, cropOptions.getAspectY());
            } else if (cropOptions.getOutputX() * cropOptions.getOutputY() > 0) {
                cropIntent.putExtra(CropExtras.KEY_OUTPUT_X, cropOptions.getOutputX());
                cropIntent.putExtra(CropExtras.KEY_OUTPUT_Y, cropOptions.getOutputY());

            } else {
                cropIntent.putExtra(CropExtras.KEY_ASPECT_X, 1);
                cropIntent.putExtra(CropExtras.KEY_ASPECT_Y, 1);
            }
            if (IntentUtils.intentAvailable(mContext, cropIntent)) {
                startCropActivityForResult(cropIntent, PHOTO_WITCH_CROP_RESULT);
            } else {
                if (takePhotoResult != null) {
                    takePhotoResult.takeFailure(new TakeException(TYPE_NO_MATCH_CROP_INTENT));
                }
            }
        } else {
            Intent cropIntentWithOtherApp = IntentUtils.getCropIntentWithOtherApp(takePhotoUri, tempUri, cropOptions);

            if (IntentUtils.intentAvailable(mContext, cropIntentWithOtherApp)) {
                startActivityForResult(cropIntentWithOtherApp, PHOTO_WITCH_CROP_RESULT);
            } else {
                if (takePhotoResult != null) {
                    takePhotoResult.takeFailure(new TakeException(TYPE_NO_MATCH_CROP_INTENT));
                }
            }
        }


    }
}