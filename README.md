아래는 안드로이드에서 카메라로 촬영 → 앱에서 데이터(URI/Bitmap/ByteArray) 얻는 대표 패턴 3가지입니다. 타깃 SDK 33+ 기준으로 안전한 방식 위주(권한 최소화)로 정리했어요.

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

원하시면 위 코드를 모듈화된 유틸 클래스(촬영 시작, Uri→Bitmap/Byte 변환, EXIF 회전, MediaStore 저장)로 깔끔하게 묶어서 드릴게요. 타깃 SDK/최소 SDK와 “단일/다중 촬영” 여부만 알려주시면 맞춤 버전으로 정리해 드리겠습니다.