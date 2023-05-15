/* Copyright 2022 Lin Yi. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

/** 本应用主要对 Tensorflow Lite Pose Estimation 示例项目的 MainActivity.kt
 *  文件进行了重写，示例项目中其余文件除了包名调整外基本无改动，原版权归
 *  The Tensorflow Authors 所有 */

package lyi.linyi.posemon

import PoseType
import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Process
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lyi.linyi.posemon.camera.CameraSource
import lyi.linyi.posemon.data.CameraData
import lyi.linyi.posemon.data.Device
import lyi.linyi.posemon.ml.ModelType
import lyi.linyi.posemon.ml.MoveNet
import lyi.linyi.posemon.ml.PoseClassifier
import mediaPlayerFlags
import poseConfigs
import poseCounterMap

class MainActivity : AppCompatActivity() {
    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
    }

    /** 为视频画面创建一个 TextureView */
    private lateinit var textureView: TextureView

    /** 修改默认计算设备：CPU、GPU、NNAPI（AI加速器） */
    private var device = Device.NNAPI
    /** 修改默认摄像头：FRONT、BACK */
    private var selectedCamera = CameraData.BACK

    /** 定义几个计数器 */
    private var missingCounter = 0

    /** 定义一个历史姿态寄存器 */
    private var poseRegister: PoseType = PoseType.STANDARD

    /** 设置一个用来显示 Debug 信息的 TextView */
    private lateinit var tvDebug: TextView

    /** 设置一个用来显示当前坐姿状态的 ImageView */
    private lateinit var ivStatus: ImageView

    private lateinit var tvFPS: TextView
    private lateinit var tvScore: TextView
    private lateinit var spnDevice: Spinner
    private lateinit var spnCamera: Spinner

    private var cameraSource: CameraSource? = null
    private var isClassifyPose = true

    /** 相机权限获取，需要拍照进行处理 */
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                /** 得到用户相机授权后，程序开始运行 */
                openCamera()
            } else {
                /** 提示用户“未获得相机权限，应用无法运行” */
                ErrorDialog.newInstance(getString(R.string.tfe_pe_request_permission))
                    .show(supportFragmentManager, FRAGMENT_DIALOG)
            }
        }

    /** 设备选择，也就是使用什么样的硬件：CPU，GPU还是NNAPI */
    private var changeDeviceListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            changeDevice(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            /** 如果用户未选择运算设备，使用默认设备进行计算 */
        }
    }

    /** 前置相机还是后置相机进行拍摄 */
    private var changeCameraListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, view: View?, direction: Int, id: Long) {
            changeCamera(direction)
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
            /** 如果用户未选择摄像头，使用默认摄像头进行拍摄 */
        }
    }

    /** 程序启动时候的一些初始化设定 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = surfaceTextureListener

        /** 程序运行时保持屏幕常亮 */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tvScore = findViewById(R.id.tvScore)

        /** 用来显示 Debug 信息 */
        tvDebug = findViewById(R.id.tvDebug)

        /** 用来显示当前坐姿状态 */
        ivStatus = findViewById(R.id.ivStatus)

        tvFPS = findViewById(R.id.tvFps)
        spnDevice = findViewById(R.id.spnDevice)
        spnCamera = findViewById(R.id.spnCamera)

        initSpinner()
        if (!isCameraPermissionGranted()) {
            requestPermission()
        }
    }

    /** surfaceTextureListener 对象附加到一个 TextureView 实例上，以便在纹理视图发生相应事件时接收通知。 */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    override fun onStart() {
        super.onStart()
        openCamera()
    }

    override fun onResume() {
        cameraSource?.resume()
        super.onResume()
    }

    override fun onPause() {
        cameraSource?.close()
        cameraSource = null
        super.onPause()
    }

    /** 检查相机权限是否有授权 */
    private fun isCameraPermissionGranted(): Boolean {
        return checkPermission(
            Manifest.permission.CAMERA,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 主要目的是初始化并打开相机。它首先检查相机权限是否已被授予，然后初始化 cameraSource 对象（如果尚未初始化）。
     * 它还创建一个用于处理检测到的姿势信息的监听器，并为每个姿势类型创建一个 MediaPlayer 实例。最后，它调用
     * createPoseEstimator() 函数以创建姿势估计器。*/
    private fun openCamera() {
        // 检查是否已获得相机权限
        if (isCameraPermissionGranted()) {
            // 如果 cameraSource 为空，则进行相机初始化操作
            if (cameraSource == null) {
                // 初始化 MediaPlayer 和标志，对于每个姿势类型和配置，如果有音频资源，则创建 MediaPlayer 实例
                val mediaPlayers = poseConfigs.mapNotNull { (poseType, config) ->
                    if (config.audioResId != 0) {
                        poseType to MediaPlayer.create(this, config.audioResId)
                    } else {
                        null
                    }
                }.toMap()

                // 创建 CameraSource 对象，并设置纹理视图、相机选择和监听器
                cameraSource =
                    CameraSource(textureView, selectedCamera, object : CameraSource.CameraSourceListener {
                        // 每秒帧数（FPS）监听器，显示 FPS 信息
                        override fun onFPSListener(fps: Int) {
                            runOnUiThread {
                                tvFPS.text = getString(R.string.tfe_pe_tv_fps, fps)
                            }
                        }

                        // 处理检测到的姿势信息
                        override fun onDetectedInfo(
                            personScore: Float?,
                            poseLabels: List<Pair<String, Float>>?
                        ) {
                            processPose(poseLabels, personScore, mediaPlayers)
                        }
                    }).apply {
                        prepareCamera()
                    }
                // 检查姿势分类器是否可用
                isPoseClassifier()
                // 使用协程在主线程上初始化相机
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                }
            }
            // 创建姿势估计器
            createPoseEstimator()
        }
    }

    /** 主要目的是处理从姿势检测器得到的姿势信息，并根据结果更新界面显示。它首先检查姿势标签和人物得分是否有效，然后根据
     * 检测到的姿势类型更新姿势计数器。接下来，它根据计数器的值更新界面上的姿势状态图像，并在一定条件下播放提示音。最后，它更新界面上的调试信息*/
    private fun processPose(poseLabels: List<Pair<String, Float>>?, personScore: Float?, mediaPlayers: Map<PoseType, MediaPlayer>) {
        // 更新界面上的得分显示
        runOnUiThread {
            tvScore.text = getString(R.string.tfe_pe_tv_score, personScore ?: 0f)
        }

        // 如果姿势标签和人物得分不为空，并且得分大于 0.3，开始处理姿势
        if (poseLabels != null && personScore != null && personScore > 0.3) {
            missingCounter = 0
            val sortedLabels = poseLabels.sortedByDescending { it.second }
            val poseType = PoseType.valueOf(sortedLabels[0].first.uppercase())
            val poseConfig = poseConfigs[poseType]

            // 更新姿势计数器
            if (poseRegister == poseType) {
                poseCounterMap[poseType] = poseCounterMap[poseType]?.plus(1) ?: 1
            } else {
                poseCounterMap[poseType] = 1
                resetCountersAndFlags(poseType)
            }
            poseRegister = poseType

            // 根据姿势计数器更新界面上的坐姿状态图像
            if (poseCounterMap[poseType]!! > 60) {
                // 播放提示音
                if (mediaPlayerFlags[poseType] == true) {
                    mediaPlayers[poseType]?.start()
                }
                mediaPlayerFlags[poseType] = false
                ivStatus.setImageResource(poseConfig?.confirmImageResource ?: R.drawable.no_target)
            } else if (poseCounterMap[poseType]!! > 30) {
                ivStatus.setImageResource(poseConfig?.imageResId ?: R.drawable.no_target)
            }

            // 更新界面上的调试信息
            runOnUiThread {
                tvDebug.text = getString(
                    R.string.tfe_pe_tv_debug,
                    "${sortedLabels[0].first} ${poseCounterMap[poseType]} ${poseConfig?.promptText}"
                )
            }
        } else {
            // 增加丢失计数器
            missingCounter++
            // 如果丢失计数器大于 30，更新界面上的图像为无目标
            if (missingCounter > 30) {
                ivStatus.setImageResource(R.drawable.no_target)
            }

            // 更新界面上的调试信息
            runOnUiThread {
                tvDebug.text = getString(R.string.tfe_pe_tv_debug, "missing $missingCounter")
            }
        }
    }


    /** 重置计数器和标志 */
    private fun resetCountersAndFlags(excludeType: PoseType) {
        poseConfigs.keys.filter { it != excludeType }.forEach { resetType ->
            poseCounterMap[resetType] = 0
            mediaPlayerFlags[resetType] = true
        }
    }



    private fun isPoseClassifier() {
        cameraSource?.setClassifier(if (isClassifyPose) PoseClassifier.create(this) else null)
    }

    /** 初始化运算设备选项菜单（CPU、GPU、NNAPI） */
    private fun initSpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_device_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spnDevice.adapter = adapter
            spnDevice.onItemSelectedListener = changeDeviceListener
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.tfe_pe_camera_name, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spnCamera.adapter = adapter
            spnCamera.onItemSelectedListener = changeCameraListener
        }
    }

    /** 在程序运行过程中切换运算设备 */
    private fun changeDevice(position: Int) {
        val targetDevice = when (position) {
            0 -> Device.CPU
            1 -> Device.GPU
            else -> Device.NNAPI
        }
        if (device == targetDevice) return
        device = targetDevice
        createPoseEstimator()
    }

    /** 在程序运行过程中切换摄像头 */
    private fun changeCamera(direaction: Int) {
        val targetCamera = when (direaction) {
            0 -> CameraData.BACK
            else -> CameraData.FRONT
        }
        if (selectedCamera == targetCamera) return
        selectedCamera = targetCamera

        cameraSource?.close()
        cameraSource = null
        openCamera()
    }

    private fun createPoseEstimator() {
        val poseDetector = MoveNet.create(this, device, ModelType.Thunder)
        poseDetector.let { detector ->
            cameraSource?.setDetector(detector)
        }
    }

    private fun requestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) -> {
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    /** 显示报错信息 */
    class ErrorDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(requireArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // pass
                }
                .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }
}
