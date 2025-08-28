#아래는 안드로이드에서 카메라로 촬영 → 앱에서 데이터(URI/Bitmap/ByteArray) 얻는 대표 패턴 3가지입니다. 타깃 SDK 33+ 기준으로 안전한 방식 위주(권한 최소화)로 정리했어요.

⸻

0) 공통 준비 (권한 & FileProvider)

<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA"/>

<application ...>
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.provider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths"/>
    </provider>
</application>

res/xml/file_paths.xml

<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="camera_cache" path="camera/"/>
    <external-files-path name="pictures" path="Pictures/"/>
</paths>

저장·선택은 MediaStore/포토 피커를 쓰면 추가 저장소 권한이 거의 필요 없습니다.

⸻

1) Activity Result API – TakePicture() (권장)
	•	결과: 촬영 이미지를 Uri로 받음 (파일 저장됨)
	•	장점: 큰 이미지(고해상도) 안전 처리, OOM 적음

class CameraActivity : AppCompatActivity() {

    private lateinit var outputUri: Uri

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // 1) Uri 그대로 사용 (업로드/표시/처리)
            onImageCaptured(outputUri)
        } else {
            // 취소/실패
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 바로 MediaStore에 pre-insert (갤러리에 자동 반영, 권장)
        outputUri = createMediaStoreImageUri()!!
        takePicture.launch(outputUri)
    }

    private fun onImageCaptured(uri: Uri) {
        // (A) 이미지 표시: Glide/Coil에 uri 전달
        // Glide.with(this).load(uri).into(imageView)

        // (B) ByteArray가 필요하다면:
        val bytes = readBytes(uri)
        // (C) Bitmap이 필요하다면(주의: 크면 샘플링 권장):
        val bitmap = loadSampledBitmap(uri, maxSize = 2048)
    }

    private fun createMediaStoreImageUri(): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyApp")
            }
        }
        return contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
    }

    private fun readBytes(uri: Uri): ByteArray =
        contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    private fun loadSampledBitmap(uri: Uri, maxSize: Int): Bitmap {
        // ① 이미지 크기만 먼저 읽기
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }

        // ② 샘플링 비율 계산
        val (w, h) = opts.outWidth to opts.outHeight
        var sample = 1
        while (w / sample > maxSize || h / sample > maxSize) sample *= 2

        // ③ 실제 디코딩
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, opts2)!!
        }
    }
}

TakePicturePreview()는 Bitmap을 바로 주지만 해상도가 낮을 수 있어 썸네일 용도에 적합합니다.

⸻

2) 인텐트 방식 – ACTION_IMAGE_CAPTURE (+ EXTRA_OUTPUT)
	•	결과: EXTRA_OUTPUT로 넘긴 Uri에 저장
	•	주의: 오래된 방식이지만 여전히 호환성 좋음. Activity Result API와 함께 쓰면 안전.

private val captureIntent = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { res ->
    if (res.resultCode == Activity.RESULT_OK) {
        onImageCaptured(outputUri) // 위 예제의 재사용
    }
}

private lateinit var outputUri: Uri

fun startCameraWithIntent() {
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    outputUri = createTempFileUri() // FileProvider로 캐시에 임시 파일
    intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    captureIntent.launch(intent)
}

private fun createTempFileUri(): Uri {
    val dir = File(cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.provider", file)
}

촬영 후 이미지를 MediaStore에 등록하고 싶으면, 캐시 파일을 copy → insert하는 단계를 추가하세요.

⸻

3) CameraX – 직접 프리뷰 & 촬영 제어 (앱 내 카메라 UX)
	•	결과: 저장된 파일의 Uri / 경로를 얻어 처리
	•	장점: 앱 내 카메라 UI, 연속 촬영, 해상도/회전/플래시 제어
	•	의존성:

implementation "androidx.camera:camera-core:<latest>"
implementation "androidx.camera:camera-camera2:<latest>"
implementation "androidx.camera:camera-lifecycle:<latest>"
implementation "androidx.camera:camera-view:<latest>"
implementation "androidx.camera:camera-extensions:<latest>"

class CameraXActivity : AppCompatActivity() {
    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previewView = PreviewView(this)
        setContentView(previewView)

        // 권한 확인 후 실행
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera(previewView)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera(findViewById(android.R.id.content).rootView as PreviewView)
        }

    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    fun takePhoto() {
        // MediaStore에 바로 저장(권장)
        val name = "IMG_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyApp")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    // 에러 처리
                }
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val savedUri = result.savedUri!!
                    onImageCaptured(savedUri) // 위 공통 처리 재사용
                }
            }
        )
    }
}


⸻

4) 얻은 데이터 활용 팁

(A) EXIF 회전 보정 (직접 Bitmap 처리 시)

fun rotateIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val input = context.contentResolver.openInputStream(uri)!!
    val exif = ExifInterface(input)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    input.close()

    val matrix = Matrix()
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    if (degrees != 0) matrix.postRotate(degrees.toFloat())
    return if (degrees != 0) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    else bitmap
}

(B) JPEG 압축하여 ByteArray 만들기

fun bitmapToJpegBytes(bm: Bitmap, quality: Int = 85): ByteArray =
    ByteArrayOutputStream().use { out ->
        bm.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

(C) 큰 파일 업로드 전 리사이즈

fun resizeBitmap(bm: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    val ratio = minOf(maxWidth / bm.width.toFloat(), maxHeight / bm.height.toFloat())
    if (ratio >= 1f) return bm
    val w = (bm.width * ratio).toInt()
    val h = (bm.height * ratio).toInt()
    return Bitmap.createScaledBitmap(bm, w, h, true)
}


⸻

5) 갤러리로부터 “가져오기” (참고)
	•	시스템 포토 피커(권장, 권한 불필요)

private val pickPhoto = registerForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri ->
    if (uri != null) onImageCaptured(uri) // 동일 처리
}

fun openPhotoPicker() {
    pickPhoto.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
}



⸻

6) 요약 선택지
	•	고화질 촬영 + 안전한 저장 + 간단 처리 → TakePicture()로 Uri 받기 (권장)
	•	앱 내 카메라 UX 필요 → CameraX (프리뷰·연속촬영·플래시 등 제어)
	•	바로 Bitmap(썸네일) → TakePicturePreview() (큰 이미지가 필요 없을 때)
	•	데이터 형태: 표시(Glide/Coil)는 Uri, 업로드는 Uri → InputStream/ByteArray, 편집은 Bitmap으로 변환 후

원하시면 위 코드를 모듈화된 유틸 클래스(촬영 시작, Uri→B
itmap/Byte 변환, EXIF 회전, MediaStore 저장)로 깔끔하게 묶어서 드릴게요. 타깃 SDK/최소 SDK와 “단일/다중 촬영” 여부만 알려주시면 맞춤 버전으로 정리해 드리겠습니다.

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class CameraSampleActivity : AppCompatActivity() {

    private lateinit var outputUri: Uri

    // 촬영 계약: 성공하면 true, outputUri 위치에 파일이 저장됨
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // ✅ 촬영 결과 Uri 확보
            onImageCaptured(outputUri)
        } else {
            // 취소/실패 처리
        }
    }

    /** 호출 시: MediaStore에 미리 파일 자리 만들고 그 Uri로 촬영 시작 */
    fun startCameraToMediaStore() {
        createMediaStoreImageUri()?.let { uri ->
            outputUri = uri
            takePicture.launch(outputUri)
        } ?: run {
            // Uri 생성 실패 처리
        }
    }

    /** MediaStore에 이미지 항목을 미리 추가하고 그 Uri를 반환 */
    private fun createMediaStoreImageUri(): Uri? {
        val name = "IMG_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+에서 갤러리 하위 폴더 지정
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyApp")
            }
        }
        return contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
    }

    private fun onImageCaptured(uri: Uri) {
        // 여기서 uri로 표시/업로드/처리
        // 예) Glide.with(this).load(uri).into(imageView)
        // 예) contentResolver.openInputStream(uri)로 바이트 읽기
    }
}

private lateinit var videoUri: Uri

// 결과: 지정한 Uri에 비디오가 저장됨
private val takeVideo = registerForActivityResult(
    ActivityResultContracts.CaptureVideo()
) { success ->
    if (success) {
        onVideoCaptured(videoUri) // ✅ 촬영된 비디오 Uri
    }
}

fun startVideoCapture() {
    videoUri = createVideoUri()!!
    takeVideo.launch(videoUri)
}

/** MediaStore에 비디오 자리 미리 만들기 */
private fun createVideoUri(): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "VID_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MyApp")
        }
    }
    return contentResolver.insert(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
    )
}

private fun onVideoCaptured(uri: Uri) {
    // (A) 비디오 재생
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    startActivity(intent)

    // (B) 업로드/처리: contentResolver.openInputStream(uri) 사용
}




좋습니다. Downloads 저장 → 공유 흐름에서 IS_PENDING을 언제 0으로 바꿔야 하는지 정확한 타이밍은 아래처럼 하시면 됩니다.

타이밍 원칙 (요점)
	1.	insert 시 IS_PENDING=1 로 등록
	2.	전부 쓰기 완료 후(스트림 flush/close까지 끝난 직후) IS_PENDING=0 으로 업데이트
	3.	그 다음에 공유 인텐트 실행 (ACTION_SEND)

즉, 공유를 띄우기 전에 반드시 0으로 변경해야 합니다. 그래야 다른 앱이 해당 content://에 접근할 수 있어요.

⸻

안전한 구현 패턴 (코루틴 + 예외 롤백 포함)

suspend fun saveToDownloadsThenShare(
    context: Context,
    url: String,
    displayName: String,
    mime: String
) = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    // 1) IS_PENDING=1 로 빈 항목 생성
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/") // 필요 시 하위 폴더
        }
    }
    val uri = resolver.insert(collection, values) ?: error("insert failed")

    try {
        // 2) 네트워크 → 파일 쓰기 (완전히 끝날 때까지)
        val req = Request.Builder().url(url).build()
        OkHttpClient().newCall(req).execute().use { resp ->
            require(resp.isSuccessful) { "HTTP ${resp.code}" }
            val input = resp.body?.byteStream() ?: error("empty body")

            // (A) 일반 OutputStream 경로
            resolver.openOutputStream(uri)?.use { out ->
                input.copyTo(out)
                out.flush() // 중요
            } ?: error("openOutputStream failed")

            // (B) 더 안전하게 fsync까지 하고 싶다면(선택):
            // resolver.openFileDescriptor(uri, "w")?.use { pfd ->
            //     FileOutputStream(pfd.fileDescriptor).channel.force(true) // fsync 유사
            // }
        }

        // 3) 쓰기가 전부 끝났다면 노출
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }

        // 4) 이제 공유 (메인 스레드에서 실행)
        withContext(Dispatchers.Main) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.also { intent ->
                context.startActivity(Intent.createChooser(intent, "공유"))
            }
        }
    } catch (e: Throwable) {
        // 실패 시 깨끗이 롤백
        resolver.delete(uri, null, null)
        throw e
    }
}

왜 이 순서가 중요한가?
	•	IS_PENDING=1 상태에서는 다른 앱이 보거나 열 수 없습니다.
	•	공유 대상 앱(Gmail/카카오톡/드라이브 등)이 파일을 읽으려면, 공유 인텐트를 띄우기 전에 IS_PENDING=0 으로 전환되어 있어야 합니다.

⸻

자주 하는 실수 & 팁
	•	❌ 쓰기 도중(아직 IS_PENDING=1)에 공유를 띄움 → 상대 앱이 빈 파일/접근 실패
	•	✅ openOutputStream(...).use { ... } 블록이 완전히 끝난 다음에 0으로 업데이트
	•	✅ 가능하면 MIME 정확히 지정 (application/pdf, image/jpeg, video/mp4 등)
	•	✅ Android 9 이하에서는 IS_PENDING이 없으므로 단순 insert→쓰기 후 곧바로 공유(단, 권한 정책 유의)
	•	✅ SAF(ACTION_CREATE_DOCUMENT)로 저장한 Uri를 공유할 때는 IS_PENDING 개념이 적용되지 않습니다(바로 공유 가능).

필요하시면 여러 파일 일괄 저장 후 일괄 공유(모두 IS_PENDING=0 전환 완료 → ACTION_SEND_MULTIPLE) 버전도 바로 구성해드릴게요.



import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

fun getDownloadFileUri(context: Context, file: File): Uri? {
    // MediaStore에서 파일명으로 검색
    val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)

    val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
    val selectionArgs = arrayOf(file.name)

    context.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    ).use { cursor ->
        if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
        }
    }
    return null
}

좋아요. Uri가 이미지/영상/애니GIF 무엇이든 “썸네일 Bitmap”을 알아서 뽑아주는 통합 유틸을 드릴게요.
아래 코드는 Kotlin + Glide 4.16.0 기준이며, content://, file://, http(s):// 모두 지원합니다.

⸻

Gradle

dependencies {
    implementation "com.github.bumptech.glide:glide:4.16.0"
    kapt "com.github.bumptech.glide:compiler:4.16.0"
}


⸻

통합 유틸 (한 파일로 복붙)

@file:Suppress("unused")

package your.pkg

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MIME으로 미디어 타입 추론
 */
private enum class MediaKind { IMAGE, GIF, VIDEO, UNKNOWN }

private fun kindFromMime(mime: String?): MediaKind = when {
    mime == null -> MediaKind.UNKNOWN
    mime.startsWith("image/") && mime != "image/gif" -> MediaKind.IMAGE
    mime == "image/gif" -> MediaKind.GIF
    mime.startsWith("video/") -> MediaKind.VIDEO
    else -> MediaKind.UNKNOWN
}

/**
 * Uri의 mimeType 얻기 (content:// 우선, 그 외엔 확장자 추론)
 */
private fun Context.resolveMimeType(uri: Uri): String? {
    return when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> contentResolver.getType(uri)
        ContentResolver.SCHEME_FILE, "http", "https" -> {
            val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (ext.isNullOrBlank()) null
            else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        }
        else -> null
    }
}

/**
 * 어떤 Uri든 적절히 처리하여 "썸네일 Bitmap"을 반환
 *
 * - 이미지: asBitmap() 그대로 로드
 * - GIF:   asBitmap() → 첫 프레임 썸네일(정적) 반환
 * - 동영상: asBitmap().frame(timeUs) → 지정 프레임 썸네일 반환
 *
 * @param uri             이미지/영상/애니GIF/원격URL/로컬 모두 가능
 * @param reqWidth        썸네일 목표 가로(px). 메모리 절약/성능 위해 반드시 지정 권장
 * @param reqHeight       썸네일 목표 세로(px)
 * @param videoFrameUs    동영상 썸네일을 뽑을 프레임 시점(마이크로초). 기본 1초 지점.
 * @param exactSize       true면 정확히 reqWidth×reqHeight로 강제(센터크롭), false면 비율 유지 축소
 */
suspend fun Context.loadUniversalThumbnailBitmap(
    uri: Uri,
    reqWidth: Int = 512,
    reqHeight: Int = 512,
    videoFrameUs: Long = 1_000_000L,
    exactSize: Boolean = false
): Bitmap = suspendCancellableCoroutine { cont ->
    val mime = resolveMimeType(uri)
    val kind = kindFromMime(mime)

    // 공통 옵션: 다운샘플링 + 사이즈 제한
    val baseOptions = RequestOptions()
        .downsample(DownsampleStrategy.AT_MOST) // 원본 과대 로딩 방지
        .override(reqWidth, reqHeight)
        .apply {
            if (exactSize) centerCrop() else fitCenter()
        }

    val rm = Glide.with(this)
    val builder = when (kind) {
        MediaKind.IMAGE -> {
            // 정적 이미지
            rm.asBitmap()
                .apply(baseOptions)
                .load(uri)
        }
        MediaKind.GIF -> {
            // GIF: asBitmap()은 첫 프레임 Bitmap 반환 → 썸네일 용도로 적합
            rm.asBitmap()
                .apply(baseOptions)
                .dontAnimate() // 첫 프레임 뽑을 때 불필요한 애니 방지
                .load(uri)
        }
        MediaKind.VIDEO -> {
            // 동영상: 특정 시점 프레임 썸네일
            rm.asBitmap()
                .apply(baseOptions)
                .frame(videoFrameUs) // 마이크로초 단위 (예: 1_000_000L = 1초)
                .load(uri)
        }
        MediaKind.UNKNOWN -> {
            // MIME 추정 실패: 가장 보편적인 경로로 시도 (이미지→비디오 순)
            // 1차: 이미지처럼 시도
            rm.asBitmap()
                .apply(baseOptions)
                .load(uri)
        }
    }

    val future = builder.submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)

    cont.invokeOnCancellation {
        rm.clear(future)
    }

    Thread {
        try {
            val bmp = future.get() // Glide 내부 스레드풀 사용 → 여기선 단순 대기
            if (cont.isActive) cont.resume(bmp)
        } catch (e: Exception) {
            if (e is CancellationException) {
                // 취소 시 clear()는 invokeOnCancellation에서 처리
            } else {
                if (cont.isActive) cont.resumeWithException(e)
            }
        } finally {
            rm.clear(future)
        }
    }.start()
}


⸻

사용 예시

// Activity/Fragment
lifecycleScope.launch {
    try {
        // 1) 갤러리 등에서 받은 content:// Uri
        val bmp1 = applicationContext.loadUniversalThumbnailBitmap(
            uri = contentUri, reqWidth = 512, reqHeight = 512
        )
        imageView.setImageBitmap(bmp1)

        // 2) 동영상: 3초 지점 프레임
        val bmp2 = applicationContext.loadUniversalThumbnailBitmap(
            uri = videoUri, reqWidth = 640, reqHeight = 360, videoFrameUs = 3_000_000L
        )
        videoThumb.setImageBitmap(bmp2)

        // 3) 원격 GIF: 첫 프레임 썸네일
        val bmp3 = applicationContext.loadUniversalThumbnailBitmap(
            uri = Uri.parse("https://example.com/anim.gif"),
            reqWidth = 400, reqHeight = 400
        )
        gifThumb.setImageBitmap(bmp3)

    } catch (t: Throwable) {
        Toast.makeText(this@MainActivity, "썸네일 실패: ${t.message}", Toast.LENGTH_SHORT).show()
    }
}


⸻

구현 팁 & 주의사항
	•	비디오 프레임 추출 실패 시(일부 코덱/원격 URL)에는 videoFrameUs를 0 또는 더 큰 값으로 바꿔 재시도하세요.
	•	OOM 예방: override()는 반드시 쓰는 걸 권장합니다. 원본 크기 로드는 피하세요.
	•	GIF 전체 재생이 필요하면 ImageView에는 asGif()를 사용하세요(이 유틸은 썸네일만 반환).
	•	권한: 갤러리/SAF가 돌려준 content://는 보통 추가 권한 없이 읽기 가능. 직접 파일경로 접근은 지양.
	•	정확한 크기 강제: 그리드 썸네일처럼 정확히 자르려면 exactSize=true로 두고, 레이아웃에 맞춰 centerCrop 사용.

필요하시면 동시에 여러 Uri를 썸네일화(병렬/배치) 하거나, 결과를 JPEG/WEBP로 파일 저장하는 유틸도 이어서 만들어 드릴게요.





