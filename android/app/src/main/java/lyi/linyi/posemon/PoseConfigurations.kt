import lyi.linyi.posemon.R

enum class PoseType {
    FORWARDHEAD,
    STANDARD,
    CROSSLEG,
    MISSING,
    CHAIR,
    COBRA,
    DOG,
    TREE,
    WARRIOR,
}

data class PoseConfig(
    val poseType: PoseType,
    val promptText: String,
    val audioResId: Int,
    val imageResId: Int,
    val confirmImageResource: Int
)

val poseConfigs = mapOf(
    PoseType.FORWARDHEAD to PoseConfig(
        PoseType.FORWARDHEAD,
        "Forward head detected",
        R.raw.forwardhead,
        R.drawable.forwardhead_suspect,
        R.drawable.forwardhead_confirm
    ),
    PoseType.STANDARD to PoseConfig(
        PoseType.STANDARD,
        "Standard pose detected",
        R.raw.standard,
        R.drawable.standard,
        R.drawable.standard
    ),
    PoseType.CROSSLEG to PoseConfig(
        PoseType.CROSSLEG,
        "Cross leg detected",
        R.raw.crossleg,
        R.drawable.crossleg_suspect,
        R.drawable.crossleg_confirm
    ),
    PoseType.MISSING to PoseConfig(
        PoseType.MISSING,
        "No person detected",
        0,
        R.drawable.no_target,
        R.drawable.no_target
    ),
    PoseType.CHAIR to PoseConfig(
        PoseType.CHAIR,
        "Yoga CHAIR detected",
        0,
        R.drawable.forwardhead_suspect,
        R.drawable.forwardhead_confirm
    ),
    PoseType.COBRA to PoseConfig(
        PoseType.COBRA,
        "Yoga COBRA detected",
        0,
        R.drawable.forwardhead_suspect,
        R.drawable.forwardhead_confirm
    ),
    PoseType.DOG to PoseConfig(
        PoseType.DOG,
        "Yoga DOG detected",
        0,
        R.drawable.standard,
        R.drawable.standard
    ),
    PoseType.TREE to PoseConfig(
        PoseType.TREE,
        "Yoga TREE detected",
        0,
        R.drawable.standard,
        R.drawable.standard
    ),
    PoseType.WARRIOR to PoseConfig(
        PoseType.WARRIOR,
        "Yoga WARRIOR detected",
        0,
        R.drawable.crossleg_suspect,
        R.drawable.crossleg_confirm
    )
)

val poseCounterMap = mutableMapOf<PoseType, Int>(
    PoseType.FORWARDHEAD to 0,
    PoseType.STANDARD to 0,
    PoseType.CROSSLEG to 0,
    PoseType.CHAIR to 0,
    PoseType.COBRA to 0,
    PoseType.DOG to 0,
    PoseType.TREE to 0,
    PoseType.WARRIOR to 0,
)

val mediaPlayerFlags = mutableMapOf<PoseType, Boolean>(
    PoseType.FORWARDHEAD to true,
    PoseType.STANDARD to true,
    PoseType.CROSSLEG to true,
    PoseType.CHAIR to true,
    PoseType.COBRA to true,
    PoseType.DOG to true,
    PoseType.TREE to true,
    PoseType.WARRIOR to true,
)
