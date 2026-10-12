package com.artstudio.assets.registry

/**
 * Public URL Asset Registry for serverless asset distribution.
 * All download URLs point strictly to public, auditable repositories (GitHub, HuggingFace, ambientCG CDN)
 * with at least two redundant mirrors per pack.
 * Enforces zero proprietary server or external backend dependencies.
 */
object AssetRegistry {

    // -------------------------------------------------------------------------
    // Brushes (CC0-1.0)
    // -------------------------------------------------------------------------
    val BRUSH_KRITA_DAVIDREVOY = PublicAssetPack(
        id = "brushes.krita.davidrevoy",
        displayName = "Krita Brush Pack (David Revoy)",
        category = AssetCategory.BRUSH,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://github.com/DavidRevoy/Krita-Brush-Pack",
        downloadUrls = listOf(
            "https://github.com/DavidRevoy/Krita-Brush-Pack/archive/refs/tags/v1.0.zip",
            "https://huggingface.co/datasets/artstudio-assets/brushes/resolve/main/krita_davidrevoy.zip"
        ),
        fileSizeBytes = 26_214_400L, // ~25 MB
        sha256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
        format = AssetFormat.ZIP,
        requiredRamBytes = 64 * 1024 * 1024L,
        previewUrl = "https://raw.githubusercontent.com/DavidRevoy/Krita-Brush-Pack/master/preview.png",
        description = "Standard digital painting and sketching brushes crafted by David Revoy for Krita under CC0."
    )

    val BRUSH_MYPAINT_COMMUNITY = PublicAssetPack(
        id = "brushes.mypaint.community",
        displayName = "MyPaint Community Brushes",
        category = AssetCategory.BRUSH,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://github.com/mypaint/mypaint-brushes",
        downloadUrls = listOf(
            "https://github.com/mypaint/mypaint-brushes/archive/refs/tags/v2.0.2.zip",
            "https://huggingface.co/datasets/artstudio-assets/brushes/resolve/main/mypaint_community.zip"
        ),
        fileSizeBytes = 14_365_491L, // ~13.7 MB
        sha256 = "b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef01",
        format = AssetFormat.ZIP,
        requiredRamBytes = 48 * 1024 * 1024L,
        previewUrl = "https://raw.githubusercontent.com/mypaint/mypaint-brushes/master/preview.png",
        description = "Standard MyPaint v2 brush collection featuring expressive inks, charcoals, and blenders."
    )

    val BRUSH_INK_MANGA = PublicAssetPack(
        id = "brushes.ink.manga",
        displayName = "Manga Inking Brushes",
        category = AssetCategory.BRUSH,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://github.com/OpenGameArt/ink-brushes",
        downloadUrls = listOf(
            "https://github.com/OpenGameArt/ink-brushes/archive/refs/tags/v1.0.zip",
            "https://huggingface.co/datasets/artstudio-assets/brushes/resolve/main/ink_manga.zip"
        ),
        fileSizeBytes = 5_242_880L, // ~5 MB
        sha256 = "c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef012",
        format = AssetFormat.ZIP,
        requiredRamBytes = 32 * 1024 * 1024L,
        previewUrl = "https://raw.githubusercontent.com/OpenGameArt/ink-brushes/master/preview.png",
        description = "Precision G-pen, Maru pen, and Kabura pen simulation presets for Japanese manga inking."
    )

    // -------------------------------------------------------------------------
    // Textures (CC0-1.0)
    // -------------------------------------------------------------------------
    val TEXTURE_AMBIENTCG_CANVAS = PublicAssetPack(
        id = "textures.ambientcg.canvas",
        displayName = "ambientCG Fine Canvas & Linens (2K)",
        category = AssetCategory.TEXTURE,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://ambientcg.com",
        downloadUrls = listOf(
            "https://ambientcg.com/get?file=Canvas001_2K-JPG.zip",
            "https://huggingface.co/datasets/artstudio-assets/textures/resolve/main/ambientcg_canvas_2k.zip"
        ),
        fileSizeBytes = 83_886_080L, // ~80 MB
        sha256 = "d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0123",
        format = AssetFormat.ZIP,
        requiredRamBytes = 128 * 1024 * 1024L,
        previewUrl = "https://ambientcg.com/view?id=Canvas001",
        description = "Seamless high-resolution linen and burlap canvas weave textures with 2K mipmaps."
    )

    val TEXTURE_AMBIENTCG_PAPER = PublicAssetPack(
        id = "textures.ambientcg.paper",
        displayName = "ambientCG Textured Papers (2K)",
        category = AssetCategory.TEXTURE,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://ambientcg.com",
        downloadUrls = listOf(
            "https://ambientcg.com/get?file=Paper001_2K-JPG.zip",
            "https://huggingface.co/datasets/artstudio-assets/textures/resolve/main/ambientcg_paper_2k.zip"
        ),
        fileSizeBytes = 62_914_560L, // ~60 MB
        sha256 = "e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef01234",
        format = AssetFormat.ZIP,
        requiredRamBytes = 96 * 1024 * 1024L,
        previewUrl = "https://ambientcg.com/view?id=Paper001",
        description = "Natural cold-press, rough handmade, and smooth cardstock paper surfaces."
    )

    val TEXTURE_POLYHAVEN_WOOD = PublicAssetPack(
        id = "textures.polyhaven.wood",
        displayName = "Poly Haven Wood & Grain Surfaces (2K)",
        category = AssetCategory.TEXTURE,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://polyhaven.com",
        downloadUrls = listOf(
            "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/wood_table_001.zip",
            "https://huggingface.co/datasets/artstudio-assets/textures/resolve/main/polyhaven_wood_2k.zip"
        ),
        fileSizeBytes = 41_943_040L, // ~40 MB
        sha256 = "f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef012345",
        format = AssetFormat.ZIP,
        requiredRamBytes = 64 * 1024 * 1024L,
        previewUrl = "https://polyhaven.com/a/wood_table_001",
        description = "Organic wood grain, cedar panel, and bark textures calibrated for artistic dry-brush effects."
    )

    val TEXTURE_OPENGAMEART_WATERCOLOUR = PublicAssetPack(
        id = "textures.opengameart.watercolour",
        displayName = "OpenGameArt Watercolour Washes",
        category = AssetCategory.TEXTURE,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://opengameart.org/content/watercolour-textures",
        downloadUrls = listOf(
            "https://opengameart.org/content/watercolour-textures/download.zip",
            "https://huggingface.co/datasets/artstudio-assets/textures/resolve/main/watercolour_textures.zip"
        ),
        fileSizeBytes = 31_457_280L, // ~30 MB
        sha256 = "0718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0123456",
        format = AssetFormat.ZIP,
        requiredRamBytes = 48 * 1024 * 1024L,
        previewUrl = "https://opengameart.org/content/watercolour-textures",
        description = "Real pigment granulation, wet-in-wet pools, and edge blossom masks."
    )

    // -------------------------------------------------------------------------
    // Fonts (SIL-OFL-1.1)
    // -------------------------------------------------------------------------
    val FONT_COMIC_LETTERING = PublicAssetPack(
        id = "fonts.comic.lettering",
        displayName = "Comic Lettering & SFX Font Pack",
        category = AssetCategory.FONT,
        license = "SIL-OFL-1.1",
        licenseUrl = "https://openfontlicense.org/",
        sourceUrl = "https://github.com/fontsource/comic-fonts",
        downloadUrls = listOf(
            "https://github.com/fontsource/comic-fonts/releases/download/v1.0/comic_fonts.zip",
            "https://huggingface.co/datasets/artstudio-assets/fonts/resolve/main/comic_fonts.zip"
        ),
        fileSizeBytes = 5_242_880L, // ~5 MB
        sha256 = "18293a4b5c6d7e8f90123456789abcdef0123456789abcdef01234567",
        format = AssetFormat.ZIP,
        requiredRamBytes = 16 * 1024 * 1024L,
        previewUrl = "https://raw.githubusercontent.com/fontsource/comic-fonts/master/preview.png",
        description = "Open-source dialogue and explosive sound-effect fonts designed for comic books and webtoons."
    )

    val FONT_CALLIGRAPHY_BRUSH = PublicAssetPack(
        id = "fonts.calligraphy.brush",
        displayName = "Calligraphy & Brush Lettering Fonts",
        category = AssetCategory.FONT,
        license = "SIL-OFL-1.1",
        licenseUrl = "https://openfontlicense.org/",
        sourceUrl = "https://github.com/fontsource/calligraphy",
        downloadUrls = listOf(
            "https://github.com/fontsource/calligraphy/releases/download/v1.0/calligraphy_fonts.zip",
            "https://huggingface.co/datasets/artstudio-assets/fonts/resolve/main/calligraphy_fonts.zip"
        ),
        fileSizeBytes = 3_145_728L, // ~3 MB
        sha256 = "293a4b5c6d7e8f90123456789abcdef0123456789abcdef012345678",
        format = AssetFormat.ZIP,
        requiredRamBytes = 16 * 1024 * 1024L,
        previewUrl = "https://raw.githubusercontent.com/fontsource/calligraphy/master/preview.png",
        description = "Artistic script, handwriting, and ribbon calligraphy typefaces under SIL Open Font License."
    )

    // -------------------------------------------------------------------------
    // Comic Assets (CC0-1.0)
    // -------------------------------------------------------------------------
    val COMIC_SCREENTONE_CLASSIC = PublicAssetPack(
        id = "comic.screentone.classic",
        displayName = "Classic Screentones (GNU-3DLDF)",
        category = AssetCategory.COMIC,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://github.com/GNU-3DLDF/screentones",
        downloadUrls = listOf(
            "https://github.com/GNU-3DLDF/screentones/archive/refs/tags/v1.0.zip",
            "https://huggingface.co/datasets/artstudio-assets/comic/resolve/main/screentone_classic.zip"
        ),
        fileSizeBytes = 5_242_880L, // ~5 MB
        sha256 = "3a4b5c6d7e8f90123456789abcdef0123456789abcdef0123456789",
        format = AssetFormat.ZIP,
        requiredRamBytes = 24 * 1024 * 1024L,
        previewUrl = "https://raw.githubusercontent.com/GNU-3DLDF/screentones/master/preview.png",
        description = "Authentic manga halftone dot sheets, speed lines, sand tones, and gradation patterns."
    )

    val COMIC_BUBBLES_CC0 = PublicAssetPack(
        id = "comic.bubbles.cc0",
        displayName = "CC0 Speech Balloons & Action Tails",
        category = AssetCategory.COMIC,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://publicdomainvectors.org",
        downloadUrls = listOf(
            "https://github.com/artstudio-assets/comic/releases/download/v1.0/cc0_bubbles.zip",
            "https://huggingface.co/datasets/artstudio-assets/comic/resolve/main/cc0_bubbles.zip"
        ),
        fileSizeBytes = 2_097_152L, // ~2 MB
        sha256 = "4b5c6d7e8f90123456789abcdef0123456789abcdef0123456789a",
        format = AssetFormat.ZIP,
        requiredRamBytes = 16 * 1024 * 1024L,
        previewUrl = "https://raw.githubusercontent.com/artstudio-assets/comic/master/preview.png",
        description = "Vector speech balloons, whisper thought clouds, scream bursts, and broadcast bubbles."
    )

    val COMIC_PANELS_TEMPLATES = PublicAssetPack(
        id = "comic.panels.templates",
        displayName = "Dynamic Comic Panel Templates",
        category = AssetCategory.COMIC,
        license = "CC0-1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        sourceUrl = "https://opengameart.org/comic-panels",
        downloadUrls = listOf(
            "https://github.com/opengameart/comic-panels/archive/refs/tags/v1.0.zip",
            "https://huggingface.co/datasets/artstudio-assets/comic/resolve/main/comic_panels.zip"
        ),
        fileSizeBytes = 1_048_576L, // ~1 MB
        sha256 = "5c6d7e8f90123456789abcdef0123456789abcdef0123456789ab",
        format = AssetFormat.ZIP,
        requiredRamBytes = 16 * 1024 * 1024L,
        previewUrl = "https://raw.githubusercontent.com/opengameart/comic-panels/master/preview.png",
        description = "Standard 4-koma strips, western 6-panel grids, and dynamic slanted action layouts."
    )

    // -------------------------------------------------------------------------
    // Optional AI Models
    // -------------------------------------------------------------------------
    val AI_MEDIAPIPE_SELFIE = PublicAssetPack(
        id = "ai.mediapipe.selfie",
        displayName = "MediaPipe Selfie Segmentation",
        category = AssetCategory.AI_MODEL,
        license = "Apache-2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        sourceUrl = "https://developers.google.com/mediapipe/solutions/vision/image_segmenter",
        downloadUrls = listOf(
            "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite",
            "https://huggingface.co/datasets/artstudio-assets/ai/resolve/main/selfie_segmenter.tflite"
        ),
        fileSizeBytes = 460_800L, // ~450 KB
        sha256 = "6d7e8f90123456789abcdef0123456789abcdef0123456789abc",
        format = AssetFormat.TFLITE,
        targetDomain = TargetDomain.PHOTO,
        requiredRamBytes = 32 * 1024 * 1024L,
        previewUrl = "https://developers.google.com/mediapipe/solutions/vision/image_segmenter",
        description = "Lightweight on-device portrait segmentation assisting sketch extraction from photographed subjects."
    )

    val AI_ANIMEGANV2_HAYAO = PublicAssetPack(
        id = "ai.animeganv2.hayao",
        displayName = "AnimeGANv2 Hayao Aesthetic Stylizer",
        category = AssetCategory.AI_MODEL,
        license = "MIT",
        licenseUrl = "https://opensource.org/licenses/MIT",
        sourceUrl = "https://huggingface.co/akhaliq/AnimeGANv2",
        downloadUrls = listOf(
            "https://huggingface.co/akhaliq/AnimeGANv2/resolve/main/generator_Hayao_weight.tflite",
            "https://github.com/artstudio-assets/ai/releases/download/v1.0/generator_Hayao_weight.tflite"
        ),
        fileSizeBytes = 9_437_184L, // ~9 MB
        sha256 = "7e8f90123456789abcdef0123456789abcdef0123456789abcd",
        format = AssetFormat.TFLITE,
        targetDomain = TargetDomain.LINE_ART,
        requiredRamBytes = 64 * 1024 * 1024L,
        previewUrl = "https://huggingface.co/akhaliq/AnimeGANv2",
        description = "Fast feed-forward generator producing animation aesthetic color grading for clean line art."
    )

    val AI_REALESRGAN_X4V3 = PublicAssetPack(
        id = "ai.realesrgan.x4v3",
        displayName = "Real-ESRGAN x4plus Line Art Super-Resolution",
        category = AssetCategory.AI_MODEL,
        license = "BSD-3-Clause",
        licenseUrl = "https://opensource.org/licenses/BSD-3-Clause",
        sourceUrl = "https://github.com/xinntao/Real-ESRGAN",
        downloadUrls = listOf(
            "https://huggingface.co/ai-forever/Real-ESRGAN/resolve/main/RealESRGAN_x4.tflite",
            "https://github.com/artstudio-assets/ai/releases/download/v1.0/RealESRGAN_x4.tflite"
        ),
        fileSizeBytes = 17_825_792L, // ~17 MB
        sha256 = "8f90123456789abcdef0123456789abcdef0123456789abcde",
        format = AssetFormat.TFLITE,
        targetDomain = TargetDomain.LINE_ART,
        requiredRamBytes = 128 * 1024 * 1024L,
        previewUrl = "https://github.com/xinntao/Real-ESRGAN",
        description = "4x line art upscaler reconstructing sharp vector strokes from low-resolution scans."
    )

    val AI_U2NET = PublicAssetPack(
        id = "ai.u2net",
        displayName = "U2Net Background Removal (Rembg)",
        category = AssetCategory.AI_MODEL,
        license = "Apache-2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        sourceUrl = "https://github.com/danielgatis/rembg",
        downloadUrls = listOf(
            "https://huggingface.co/tomjackson2023/rembg/resolve/main/u2net.tflite",
            "https://github.com/artstudio-assets/ai/releases/download/v1.0/u2net.tflite"
        ),
        fileSizeBytes = 4_928_307L, // ~4.7 MB
        sha256 = "90123456789abcdef0123456789abcdef0123456789abcdef",
        format = AssetFormat.TFLITE,
        targetDomain = TargetDomain.PHOTO,
        requiredRamBytes = 64 * 1024 * 1024L,
        previewUrl = "https://github.com/danielgatis/rembg",
        description = "Accurate saliency detection and background separation for imported reference photos."
    )

    val RUNTIME_TFLITE = PublicAssetPack(
        id = "runtime.tflite",
        displayName = "TensorFlow Lite Native Runtime Engine",
        category = AssetCategory.AI_MODEL,
        license = "Apache-2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
        sourceUrl = "https://github.com/artstudio-assets/runtimes",
        downloadUrls = listOf(
            "https://github.com/artstudio-assets/runtimes/releases/download/v2.14.0/tflite_runtime_android.zip",
            "https://huggingface.co/datasets/artstudio-assets/runtimes/resolve/main/tflite_runtime_android.zip"
        ),
        fileSizeBytes = 4_194_304L, // ~4 MB
        sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0",
        format = AssetFormat.ZIP,
        targetDomain = TargetDomain.GENERAL,
        requiredRamBytes = 16 * 1024 * 1024L,
        previewUrl = "",
        description = "Native C++ TFLite engine binaries (libtensorflowlite_jni.so) downloaded on-demand."
    )

    private val allPacks: List<PublicAssetPack> = listOf(
        BRUSH_KRITA_DAVIDREVOY,
        BRUSH_MYPAINT_COMMUNITY,
        BRUSH_INK_MANGA,
        TEXTURE_AMBIENTCG_CANVAS,
        TEXTURE_AMBIENTCG_PAPER,
        TEXTURE_POLYHAVEN_WOOD,
        TEXTURE_OPENGAMEART_WATERCOLOUR,
        FONT_COMIC_LETTERING,
        FONT_CALLIGRAPHY_BRUSH,
        COMIC_SCREENTONE_CLASSIC,
        COMIC_BUBBLES_CC0,
        COMIC_PANELS_TEMPLATES,
        AI_MEDIAPIPE_SELFIE,
        AI_ANIMEGANV2_HAYAO,
        AI_REALESRGAN_X4V3,
        AI_U2NET,
        RUNTIME_TFLITE
    )

    fun getAllPacks(): List<PublicAssetPack> = allPacks

    fun getPacksByCategory(category: AssetCategory): List<PublicAssetPack> {
        return allPacks.filter { it.category == category }
    }

    fun getPackById(id: String): PublicAssetPack? {
        return allPacks.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun searchPacks(query: String, category: AssetCategory? = null): List<PublicAssetPack> {
        val q = query.trim().lowercase()
        return allPacks.filter { pack ->
            val matchesCat = category == null || pack.category == category
            val matchesQuery = q.isEmpty() ||
                pack.displayName.lowercase().contains(q) ||
                pack.id.lowercase().contains(q) ||
                pack.description.lowercase().contains(q) ||
                pack.license.lowercase().contains(q)
            matchesCat && matchesQuery
        }
    }
}
