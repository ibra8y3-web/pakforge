package com.example.core.sdk

import com.example.core.privileged.BackendAvailability
import com.example.core.privileged.PrivilegedBackendKind
import com.example.core.privileged.PrivilegedBridgeSnapshot
import java.util.Locale

fun activeAppLangCode(raw: String = Locale.getDefault().language): String {
    val clean = raw.lowercase().substringBefore('-').substringBefore('_')
    return when (clean) {
        "ar", "es", "fr", "de", "zh", "en" -> clean
        else -> "en"
    }
}

data class LocalizedPluginCopy(
    val name: String,
    val description: String,
    val highlights: List<String> = emptyList()
)

data class LocalizedDocSection(
    val title: String,
    val body: String
)

data class LocalizedInterfaceSummary(
    val name: String,
    val permissionKey: String,
    val methods: String
)

/**
 * Complete 6-language localization provider (AR, EN, ES, FR, DE, ZH) for Plugin Store,
 * Built-In SDK Samples, Security Scanner findings, Smart Recognition reasons, SDK Docs,
 * and Privileged Shell Backends inside Settings.
 */
object PluginLocalization {

    fun permissionTitle(perm: PluginPermission, lang: String): String = when (activeAppLangCode(lang)) {
        "ar" -> perm.titleAr
        "es" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "Hardware y Telemetría del Dispositivo"
            PluginPermission.READ_DIAGNOSTICS -> "Diagnóstico del Motor y Shell"
            PluginPermission.READ_REPORTS -> "Informes e Historial de Compresión"
            PluginPermission.SEND_NOTIFICATIONS -> "Notificaciones del Sistema y App"
            PluginPermission.ACCESS_HOST_TOOLS -> "Herramientas de Compresión y Auditoría"
            PluginPermission.PRIVILEGED_SHELL_READ -> "Telemetría UID Root / Shizuku"
        }
        "fr" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "Matériel et Télémétrie Appareil"
            PluginPermission.READ_DIAGNOSTICS -> "Diagnostics Moteur et Shell"
            PluginPermission.READ_REPORTS -> "Rapports et Historique de Compression"
            PluginPermission.SEND_NOTIFICATIONS -> "Notifications Système et App"
            PluginPermission.ACCESS_HOST_TOOLS -> "Outils de Compression et d'Audit"
            PluginPermission.PRIVILEGED_SHELL_READ -> "Télémétrie UID Root / Shizuku"
        }
        "de" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "Geräte-Hardware & Telemetrie"
            PluginPermission.READ_DIAGNOSTICS -> "Engine-Diagnose & Shell-Status"
            PluginPermission.READ_REPORTS -> "Komprimierungsberichte & Verlauf"
            PluginPermission.SEND_NOTIFICATIONS -> "System- & App-Benachrichtigungen"
            PluginPermission.ACCESS_HOST_TOOLS -> "Host-Komprimierungs- & Prüftools"
            PluginPermission.PRIVILEGED_SHELL_READ -> "Root- / Shizuku-UID-Telemetrie"
        }
        "zh" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "设备硬件与遥测读取"
            PluginPermission.READ_DIAGNOSTICS -> "引擎诊断与 Shell 状态"
            PluginPermission.READ_REPORTS -> "压缩报告与历史记录"
            PluginPermission.SEND_NOTIFICATIONS -> "系统与应用通知发送"
            PluginPermission.ACCESS_HOST_TOOLS -> "宿主压缩与审计工具"
            PluginPermission.PRIVILEGED_SHELL_READ -> "Root / Shizuku UID 遥测"
        }
        else -> perm.titleEn
    }

    fun permissionRiskSummary(perm: PluginPermission, lang: String): String = when (activeAppLangCode(lang)) {
        "ar" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "قراءة الرام المتاحة، أنوية المعالج، المعماريات وحالة توفير الطاقة"
            PluginPermission.READ_DIAGNOSTICS -> "قراءة فحوصات التوافق ومخاطر المانيفست وحالة Root/Sui/Shizuku"
            PluginPermission.READ_REPORTS -> "قراءة تقارير التوفير السابقة وإحصائيات SHA-256 JSON"
            PluginPermission.SEND_NOTIFICATIONS -> "إرسال إشعارات لمركز إشعارات أندرويد وسجل الإضافات"
            PluginPermission.ACCESS_HOST_TOOLS -> "تشغيل فحص SHA-256 وتلخيص التوافق وتطبيق قواعد الضغط"
            PluginPermission.PRIVILEGED_SHELL_READ -> "الاستعلام عن معرّف UID لـ Root/Sui/Shizuku دون تنفيذ أوامر ضارة"
        }
        "es" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "Lee RAM, núcleos CPU, ABIs, versión SDK y ahorro de batería"
            PluginPermission.READ_DIAGNOSTICS -> "Lee compatibilidad, riesgos del manifiesto y estado Root/Sui/Shizuku"
            PluginPermission.READ_REPORTS -> "Lee informes anteriores, bytes ahorrados y auditorías SHA-256"
            PluginPermission.SEND_NOTIFICATIONS -> "Envía notificaciones al sistema Android y al registro de plugins"
            PluginPermission.ACCESS_HOST_TOOLS -> "Ejecuta hashes SHA-256, análisis y ajustes de compresión"
            PluginPermission.PRIVILEGED_SHELL_READ -> "Consulta el UID de Root/Sui/Shizuku sin comandos destructivos"
        }
        "fr" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "Lit la RAM, cœurs CPU, ABIs, version SDK et économie d'énergie"
            PluginPermission.READ_DIAGNOSTICS -> "Lit la compatibilité, risques du manifeste et état Root/Sui/Shizuku"
            PluginPermission.READ_REPORTS -> "Lit les rapports passés, octets économisés et audits SHA-256"
            PluginPermission.SEND_NOTIFICATIONS -> "Envoie des notifications Android et dans le flux des plugins"
            PluginPermission.ACCESS_HOST_TOOLS -> "Exécute les empreintes SHA-256 et préréglages de compression"
            PluginPermission.PRIVILEGED_SHELL_READ -> "Consulte l'UID Root/Sui/Shizuku sans commande destructive"
        }
        "de" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "Liest RAM, CPU-Kerne, ABIs, SDK-Version und Energiesparmodus"
            PluginPermission.READ_DIAGNOSTICS -> "Liest Kompatibilität, Manifest-Risiken und Root/Sui/Shizuku-Status"
            PluginPermission.READ_REPORTS -> "Liest frühere Berichte, gesparte Bytes und SHA-256-Audits"
            PluginPermission.SEND_NOTIFICATIONS -> "Sendet System- und In-App-Plugin-Benachrichtigungen"
            PluginPermission.ACCESS_HOST_TOOLS -> "Nutzt SHA-256-Prüfungen und Komprimierungsprofile"
            PluginPermission.PRIVILEGED_SHELL_READ -> "Fragt die Root/Sui/Shizuku-UID schreibgeschützt ab"
        }
        "zh" -> when (perm) {
            PluginPermission.READ_DEVICE_TELEMETRY -> "读取剩余内存、CPU 核心、ABI、SDK 版本与省电模式"
            PluginPermission.READ_DIAGNOSTICS -> "读取兼容性检查、清单风险与 Root/Sui/Shizuku 状态"
            PluginPermission.READ_REPORTS -> "读取历史压缩报告、节省空间及 SHA-256 审计记录"
            PluginPermission.SEND_NOTIFICATIONS -> "向 Android 通知栏与插件动态发送通知"
            PluginPermission.ACCESS_HOST_TOOLS -> "调用 SHA-256 计算、兼容性检查与压缩预设"
            PluginPermission.PRIVILEGED_SHELL_READ -> "只读查询 Root/Sui/Shizuku 的 UID，不执行破坏性指令"
        }
        else -> perm.riskSummaryEn
    }

    fun categoryTitle(cat: PluginCategory, lang: String): String = when (activeAppLangCode(lang)) {
        "ar" -> cat.titleAr
        "es" -> when (cat) {
            PluginCategory.COMPRESSION -> "Compresión y Reducción"
            PluginCategory.SYSTEM_SHELL -> "Sistema y Shell Privilegiado"
            PluginCategory.SECURITY_AUDIT -> "Seguridad y Auditoría"
            PluginCategory.PERFORMANCE -> "Rendimiento y Batería"
            PluginCategory.DEVELOPER_TOOL -> "Herramientas Dev"
        }
        "fr" -> when (cat) {
            PluginCategory.COMPRESSION -> "Compression & Allègement"
            PluginCategory.SYSTEM_SHELL -> "Système & Shell Privilégié"
            PluginCategory.SECURITY_AUDIT -> "Sécurité & Audit"
            PluginCategory.PERFORMANCE -> "Performance & Batterie"
            PluginCategory.DEVELOPER_TOOL -> "Outils Développeur"
        }
        "de" -> when (cat) {
            PluginCategory.COMPRESSION -> "Komprimierung & Schlankheit"
            PluginCategory.SYSTEM_SHELL -> "System & Privilegierte Shell"
            PluginCategory.SECURITY_AUDIT -> "Sicherheit & Audit"
            PluginCategory.PERFORMANCE -> "Leistung & Akku"
            PluginCategory.DEVELOPER_TOOL -> "Entwicklertools"
        }
        "zh" -> when (cat) {
            PluginCategory.COMPRESSION -> "压缩与精简"
            PluginCategory.SYSTEM_SHELL -> "系统与特权 Shell"
            PluginCategory.SECURITY_AUDIT -> "安全与审计"
            PluginCategory.PERFORMANCE -> "性能与电池"
            PluginCategory.DEVELOPER_TOOL -> "开发者工具"
        }
        else -> cat.titleEn
    }

    fun actionStepTitle(step: PluginActionStep, lang: String): String = when (activeAppLangCode(lang)) {
        "ar" -> step.titleAr
        "es" -> when (step) {
            PluginActionStep.CHECK_DEVICE_HEADROOM -> "Auditar RAM, CPU y Búfer"
            PluginActionStep.AUDIT_SHELL_BACKEND -> "Verificar UID Root/Sui/Shizuku"
            PluginActionStep.INSPECT_ACTIVE_PACKAGE -> "Inspeccionar APK Activo"
            PluginActionStep.APPLY_COMPRESSION_RULE -> "Aplicar Reglas de Compresión"
            PluginActionStep.COMPUTE_PACKAGE_DIGEST -> "Calcular SHA-256 y Compatibilidad"
            PluginActionStep.READ_SAVINGS_HISTORY -> "Leer Historial de Ahorro"
            PluginActionStep.DISPATCH_NOTIFICATION -> "Enviar Notificación"
        }
        "fr" -> when (step) {
            PluginActionStep.CHECK_DEVICE_HEADROOM -> "Auditer RAM, CPU & Tampon"
            PluginActionStep.AUDIT_SHELL_BACKEND -> "Vérifier UID Root/Sui/Shizuku"
            PluginActionStep.INSPECT_ACTIVE_PACKAGE -> "Inspecter l'APK Actif"
            PluginActionStep.APPLY_COMPRESSION_RULE -> "Appliquer Préréglages"
            PluginActionStep.COMPUTE_PACKAGE_DIGEST -> "Calculer SHA-256 & Compatibilité"
            PluginActionStep.READ_SAVINGS_HISTORY -> "Lire Historique des Gains"
            PluginActionStep.DISPATCH_NOTIFICATION -> "Envoyer Notification"
        }
        "de" -> when (step) {
            PluginActionStep.CHECK_DEVICE_HEADROOM -> "RAM, CPU & Puffer prüfen"
            PluginActionStep.AUDIT_SHELL_BACKEND -> "Root/Sui/Shizuku-UID prüfen"
            PluginActionStep.INSPECT_ACTIVE_PACKAGE -> "Aktives APK analysieren"
            PluginActionStep.APPLY_COMPRESSION_RULE -> "Komprimierungsregeln anwenden"
            PluginActionStep.COMPUTE_PACKAGE_DIGEST -> "SHA-256 & Kompatibilität prüfen"
            PluginActionStep.READ_SAVINGS_HISTORY -> "Sparverlauf lesen"
            PluginActionStep.DISPATCH_NOTIFICATION -> "Benachrichtigung senden"
        }
        "zh" -> when (step) {
            PluginActionStep.CHECK_DEVICE_HEADROOM -> "检查内存、CPU 与缓冲区"
            PluginActionStep.AUDIT_SHELL_BACKEND -> "检查 Root/Sui/Shizuku UID"
            PluginActionStep.INSPECT_ACTIVE_PACKAGE -> "检查当前 APK 结构"
            PluginActionStep.APPLY_COMPRESSION_RULE -> "应用智能压缩规则"
            PluginActionStep.COMPUTE_PACKAGE_DIGEST -> "计算 SHA-256 与兼容性"
            PluginActionStep.READ_SAVINGS_HISTORY -> "读取历史节省记录"
            PluginActionStep.DISPATCH_NOTIFICATION -> "发送插件通知"
        }
        else -> step.titleEn
    }

    fun trustBadge(trust: SignatureTrustLevel, lang: String): String = when (activeAppLangCode(lang)) {
        "ar" -> trust.badgeAr
        "es" -> when (trust) {
            SignatureTrustLevel.VERIFIED_OFFICIAL -> "Firma Oficial"
            SignatureTrustLevel.VERIFIED_DEVELOPER -> "Firma ECDSA Dev"
            SignatureTrustLevel.BUILTIN -> "SDK Integrado"
            SignatureTrustLevel.UNSIGNED -> "Sin Firma"
            SignatureTrustLevel.TAMPERED_REJECTED -> "Firma Inválida / Alterada"
        }
        "fr" -> when (trust) {
            SignatureTrustLevel.VERIFIED_OFFICIAL -> "Signé Officiel"
            SignatureTrustLevel.VERIFIED_DEVELOPER -> "Signé ECDSA Dev"
            SignatureTrustLevel.BUILTIN -> "SDK Intégré"
            SignatureTrustLevel.UNSIGNED -> "Non Signé"
            SignatureTrustLevel.TAMPERED_REJECTED -> "Signature Invalide / Altérée"
        }
        "de" -> when (trust) {
            SignatureTrustLevel.VERIFIED_OFFICIAL -> "Offiziell signiert"
            SignatureTrustLevel.VERIFIED_DEVELOPER -> "Dev ECDSA-signiert"
            SignatureTrustLevel.BUILTIN -> "Integriertes SDK"
            SignatureTrustLevel.UNSIGNED -> "Unsigniert"
            SignatureTrustLevel.TAMPERED_REJECTED -> "Signatur ungültig"
        }
        "zh" -> when (trust) {
            SignatureTrustLevel.VERIFIED_OFFICIAL -> "官方已签名"
            SignatureTrustLevel.VERIFIED_DEVELOPER -> "开发者 ECDSA 签名"
            SignatureTrustLevel.BUILTIN -> "内置 SDK"
            SignatureTrustLevel.UNSIGNED -> "未签名清单"
            SignatureTrustLevel.TAMPERED_REJECTED -> "签名不匹配 / 已篡改"
        }
        else -> trust.badgeEn
    }

    fun riskTierLabel(tier: SecurityRiskTier, lang: String): String = when (activeAppLangCode(lang)) {
        "ar" -> when (tier) {
            SecurityRiskTier.LOW -> "خطر منخفض"
            SecurityRiskTier.MODERATE -> "متوسط"
            SecurityRiskTier.ELEVATED -> "صلاحيات مرتفعة"
            SecurityRiskTier.BLOCKED -> "محظور"
        }
        "es" -> when (tier) {
            SecurityRiskTier.LOW -> "RIESGO BAJO"
            SecurityRiskTier.MODERATE -> "MODERADO"
            SecurityRiskTier.ELEVATED -> "ELEVADO"
            SecurityRiskTier.BLOCKED -> "BLOQUEADO"
        }
        "fr" -> when (tier) {
            SecurityRiskTier.LOW -> "RISQUE FAIBLE"
            SecurityRiskTier.MODERATE -> "MODÉRÉ"
            SecurityRiskTier.ELEVATED -> "ÉLEVÉ"
            SecurityRiskTier.BLOCKED -> "BLOQUÉ"
        }
        "de" -> when (tier) {
            SecurityRiskTier.LOW -> "GERING"
            SecurityRiskTier.MODERATE -> "MITTEL"
            SecurityRiskTier.ELEVATED -> "ERHÖHT"
            SecurityRiskTier.BLOCKED -> "BLOCKIERT"
        }
        "zh" -> when (tier) {
            SecurityRiskTier.LOW -> "低风险"
            SecurityRiskTier.MODERATE -> "中风险"
            SecurityRiskTier.ELEVATED -> "高权限"
            SecurityRiskTier.BLOCKED -> "已阻止"
        }
        else -> tier.name
    }

    fun manifestCopy(manifest: PluginManifest, lang: String): LocalizedPluginCopy {
        val code = activeAppLangCode(lang)
        val permsCount = manifest.permissions.size
        val stepsCount = manifest.actions.size
        val catLabel = categoryTitle(manifest.category, code)

        return when (manifest.id) {
            "dev.pakforge.sample.telemetry_guard" -> when (code) {
                "ar" -> LocalizedPluginCopy(
                    name = "حارس صحة الجهاز ومحرك الصلاحيات",
                    description = "يفحص الذاكرة المتاحة والأنوية وحالة Root/Sui/Shizuku، ويضبط مخزن الضغط تلقائياً ويرسل تنبيهاً تشخيصياً.",
                    highlights = listOf(catLabel, "$permsCount صلاحيات", "$stepsCount خطوات ذكية")
                )
                "es" -> LocalizedPluginCopy(
                    name = "Monitor de Salud del Dispositivo y Shell",
                    description = "Audita RAM/CPU, estado Root/Sui/Shizuku, ajusta el búfer de compresión y envía alerta diagnóstica.",
                    highlights = listOf(catLabel, "$permsCount permisos", "$stepsCount pasos")
                )
                "fr" -> LocalizedPluginCopy(
                    name = "Gardien Santé Appareil & Shell",
                    description = "Analyse RAM/CPU, état Root/Sui/Shizuku, ajuste le tampon de compression et envoie une alerte.",
                    highlights = listOf(catLabel, "$permsCount permissions", "$stepsCount étapes")
                )
                "de" -> LocalizedPluginCopy(
                    name = "Geräte- & Shell-Wächter",
                    description = "Prüft RAM/CPU, Root/Sui/Shizuku-Status, optimiert Puffergröße und sendet Diagnose-Hinweis.",
                    highlights = listOf(catLabel, "$permsCount Berechtigungen", "$stepsCount Schritte")
                )
                "zh" -> LocalizedPluginCopy(
                    name = "设备与 Shell 健康卫士",
                    description = "检测可用内存、CPU 及 Root/Sui/Shizuku 状态，自动调优压缩缓冲区并发送诊断通知。",
                    highlights = listOf(catLabel, "$permsCount 项权限", "$stepsCount 个步骤")
                )
                else -> LocalizedPluginCopy(manifest.name, manifest.description, listOf(catLabel, "$permsCount permissions", "$stepsCount steps"))
            }

            "dev.pakforge.sample.release_auditor" -> when (code) {
                "ar" -> LocalizedPluginCopy(
                    name = "مدقق تقارير الإصدار والتكرار",
                    description = "يقرأ سجل التوفير السابق من ReportsInterface وملخص التوافق ويحسب بصمة SHA-256 للحزمة.",
                    highlights = listOf(catLabel, "$permsCount صلاحيات")
                )
                "es" -> LocalizedPluginCopy(
                    name = "Auditor de Informes y Bloat",
                    description = "Lee historial de ahorro, resumen de compatibilidad y calcula huella SHA-256.",
                    highlights = listOf(catLabel, "$permsCount permisos")
                )
                "fr" -> LocalizedPluginCopy(
                    name = "Auditeur de Rapports & Bloat",
                    description = "Lit l'historique des gains, le résumé de compatibilité et calcule l'empreinte SHA-256.",
                    highlights = listOf(catLabel, "$permsCount permissions")
                )
                "de" -> LocalizedPluginCopy(
                    name = "Release- & Bloat-Prüfer",
                    description = "Liest Sparverlauf, Kompatibilitätsbericht und berechnet SHA-256-Prüfsumme.",
                    highlights = listOf(catLabel, "$permsCount Berechtigungen")
                )
                "zh" -> LocalizedPluginCopy(
                    name = "发布报告与冗余审计器",
                    description = "读取历史节省报告、兼容性摘要并计算安装包 SHA-256 指纹。",
                    highlights = listOf(catLabel, "$permsCount 项权限")
                )
                else -> LocalizedPluginCopy(manifest.name, manifest.description, listOf(catLabel, "$permsCount permissions"))
            }

            "dev.pakforge.sample.ar_en_slimmer" -> when (code) {
                "ar" -> LocalizedPluginCopy(
                    name = "وصفة تقليص الشاملة (العربية/الإنجليزية)",
                    description = "يطبق وضع الضغط الفائق مع الإبقاء على العربية والإنجليزية فقط، وضغط الصور، ودمج الحزم وتجريد sharedUserId.",
                    highlights = listOf(catLabel, "$permsCount صلاحيات", "$stepsCount خطوات ذكية")
                )
                "es" -> LocalizedPluginCopy(
                    name = "Preajuste Reductor Universal AR/EN",
                    description = "Aplica Ultra Shrink conservando árabe e inglés, optimiza imágenes, fusiona splits y quita sharedUserId.",
                    highlights = listOf(catLabel, "$permsCount permisos", "$stepsCount pasos")
                )
                "fr" -> LocalizedPluginCopy(
                    name = "Préréglage Réducteur Universel AR/EN",
                    description = "Applique Ultra Shrink (AR/EN uniquement), optimise les images, fusionne les splits et retire sharedUserId.",
                    highlights = listOf(catLabel, "$permsCount permissions", "$stepsCount étapes")
                )
                "de" -> LocalizedPluginCopy(
                    name = "AR/EN Universal-Schlankheits-Preset",
                    description = "Aktiviert Ultra Shrink (nur AR/EN), komprimiert Bilder, führt Splits zusammen und entfernt sharedUserId.",
                    highlights = listOf(catLabel, "$permsCount Berechtigungen", "$stepsCount Schritte")
                )
                "zh" -> LocalizedPluginCopy(
                    name = "双语（阿/英）通用瘦身预设",
                    description = "启用极限压缩，仅保留阿/英语言资源，压缩大图、合并 Split APK 并移除 sharedUserId。",
                    highlights = listOf(catLabel, "$permsCount 项权限", "$stepsCount 个步骤")
                )
                else -> LocalizedPluginCopy(manifest.name, manifest.description, listOf(catLabel, "$permsCount permissions", "$stepsCount steps"))
            }

            "dev.pakforge.store.system_trichrome_unlocker" -> when (code) {
                "ar" -> LocalizedPluginCopy(
                    name = "معالج تطبيقات النظام ومكتبات Trichrome",
                    description = "يكتشف تلقائياً تعارض android:sharedUserId وبصمة certDigest للمكتبات الثابتة، ويجهز إعادة توقيع المكتبات المرافقة وفحص UID لـ Root/Sui/Shizuku.",
                    highlights = listOf(catLabel, "$permsCount صلاحيات", "$stepsCount خطوات ذكية")
                )
                "es" -> LocalizedPluginCopy(
                    name = "Desbloqueador de Sistema y Trichrome",
                    description = "Detecta conflictos sharedUserId y certDigest, re-firma librerías estáticas y verifica UID Root/Sui/Shizuku.",
                    highlights = listOf(catLabel, "$permsCount permisos", "$stepsCount pasos")
                )
                "fr" -> LocalizedPluginCopy(
                    name = "Déverrouilleur Système & Trichrome",
                    description = "Détecte sharedUserId et certDigest, re-signe les bibliothèques statiques et vérifie l'UID Root/Sui/Shizuku.",
                    highlights = listOf(catLabel, "$permsCount permissions", "$stepsCount étapes")
                )
                "de" -> LocalizedPluginCopy(
                    name = "System- & Trichrome-Entsperrer",
                    description = "Erkennt sharedUserId- & certDigest-Konflikte, signiert statische Bibliotheken neu und prüft Root/Sui/Shizuku-UID.",
                    highlights = listOf(catLabel, "$permsCount Berechtigungen", "$stepsCount Schritte")
                )
                "zh" -> LocalizedPluginCopy(
                    name = "系统应用与 Trichrome 静态库解锁器",
                    description = "自动检测 sharedUserId 与静态库 certDigest 冲突，重签伴生库并校验 Root/Sui/Shizuku UID。",
                    highlights = listOf(catLabel, "$permsCount 项权限", "$stepsCount 个步骤")
                )
                else -> LocalizedPluginCopy(manifest.name, manifest.description, listOf(catLabel, "$permsCount permissions", "$stepsCount steps"))
            }

            "dev.pakforge.store.game_bundle_universalizer" -> when (code) {
                "ar" -> LocalizedPluginCopy(
                    name = "دامج حزم الألعاب والتطبيقات الثقيلة (Anti-Split)",
                    description = "يدمج الحزم المقسمة في ملف APK موقّع موحّد مع ضغط صور PNG/WebP الثقيلة وتقليم المعماريات واللغات غير الضرورية.",
                    highlights = listOf(catLabel, "$permsCount صلاحيات", "$stepsCount خطوات ذكية")
                )
                "es" -> LocalizedPluginCopy(
                    name = "Universalizador de Juegos y Splits Pesados",
                    description = "Fusiona splits en un único APK firmado, optimizando imágenes PNG/WebP y librerías ABI.",
                    highlights = listOf(catLabel, "$permsCount permisos", "$stepsCount pasos")
                )
                "fr" -> LocalizedPluginCopy(
                    name = "Universaliseur de Jeux & Splits Lourds",
                    description = "Fusionne les splits en un APK universel signé tout en compressant PNG/WebP et ABI.",
                    highlights = listOf(catLabel, "$permsCount permissions", "$stepsCount étapes")
                )
                "de" -> LocalizedPluginCopy(
                    name = "Schwerer Split- & Game-Universalizer",
                    description = "Führt Split-APKs zu einer signierten Universal-APK zusammen inkl. PNG/WebP- & ABI-Optimierung.",
                    highlights = listOf(catLabel, "$permsCount Berechtigungen", "$stepsCount Schritte")
                )
                "zh" -> LocalizedPluginCopy(
                    name = "大型拆分包与游戏单包合成器",
                    description = "将多拆分包合并为单一已签名 APK，同时优化 PNG/WebP 大图及冗余 ABI 原生库。",
                    highlights = listOf(catLabel, "$permsCount 项权限", "$stepsCount 个步骤")
                )
                else -> LocalizedPluginCopy(manifest.name, manifest.description, listOf(catLabel, "$permsCount permissions", "$stepsCount steps"))
            }

            "dev.pakforge.store.privacy_bloat_inspector" -> when (code) {
                "ar" -> LocalizedPluginCopy(
                    name = "مدقق الأمان والتكرار والمساحة المُوفَّرة",
                    description = "يفحص مخاطر التوافق، ويحسب بصمات SHA-256، ويلخّص إجمالي المساحة المُحرَّرة عبر كل العمليات مع كشف التكرار.",
                    highlights = listOf(catLabel, "$permsCount صلاحيات", "$stepsCount خطوات ذكية")
                )
                "es" -> LocalizedPluginCopy(
                    name = "Inspector de Seguridad, Bloat y Ahorro",
                    description = "Escanea riesgos de compatibilidad, calcula SHA-256 y resume el almacenamiento recuperado.",
                    highlights = listOf(catLabel, "$permsCount permisos", "$stepsCount pasos")
                )
                "fr" -> LocalizedPluginCopy(
                    name = "Inspecteur Sécurité, Bloat & Gains",
                    description = "Analyse les risques de compatibilité, calcule SHA-256 et résume l'espace économisé.",
                    highlights = listOf(catLabel, "$permsCount permissions", "$stepsCount étapes")
                )
                "de" -> LocalizedPluginCopy(
                    name = "Sicherheits-, Bloat- & Spar-Inspektor",
                    description = "Prüft Kompatibilitätsrisiken, berechnet SHA-256 und fasst den gesparten Speicher zusammen.",
                    highlights = listOf(catLabel, "$permsCount Berechtigungen", "$stepsCount Schritte")
                )
                "zh" -> LocalizedPluginCopy(
                    name = "安全、冗余与节省空间深度审计",
                    description = "扫描兼容性风险、计算 SHA-256 指纹，汇总历史节省空间并提示可调试/重复资源问题。",
                    highlights = listOf(catLabel, "$permsCount 项权限", "$stepsCount 个步骤")
                )
                else -> LocalizedPluginCopy(manifest.name, manifest.description, listOf(catLabel, "$permsCount permissions", "$stepsCount steps"))
            }

            "dev.pakforge.store.eco_battery_safeguard" -> when (code) {
                "ar" -> LocalizedPluginCopy(
                    name = "حارس الذاكرة الاقتصادية والبطارية",
                    description = "يراقب الرام المتاحة ووضع توفير الطاقة لتثبيت مخزن I/O على الوضع الاقتصادي ومنع نفاد الذاكرة أثناء ضغط الحزم الضخمة.",
                    highlights = listOf(catLabel, "$permsCount صلاحيات", "$stepsCount خطوات ذكية")
                )
                "es" -> LocalizedPluginCopy(
                    name = "Protector Eco de RAM y Batería",
                    description = "Supervisa RAM libre y modo ahorro para fijar búferes Eco y evitar OOM en APKs grandes.",
                    highlights = listOf(catLabel, "$permsCount permisos", "$stepsCount pasos")
                )
                "fr" -> LocalizedPluginCopy(
                    name = "Protecteur Éco RAM & Batterie",
                    description = "Surveille RAM libre et économie d'énergie pour limiter le tampon E/S et éviter tout OOM.",
                    highlights = listOf(catLabel, "$permsCount permissions", "$stepsCount étapes")
                )
                "de" -> LocalizedPluginCopy(
                    name = "Öko-RAM- & Akku-Schutz",
                    description = "Überwacht freien RAM & Energiesparmodus und aktiviert Eco-Puffer gegen OOM bei großen APKs.",
                    highlights = listOf(catLabel, "$permsCount Berechtigungen", "$stepsCount Schritte")
                )
                "zh" -> LocalizedPluginCopy(
                    name = "低内存与省电守护工具",
                    description = "实时监测剩余内存与省电模式，自动锁定低内存缓冲区以防止处理超大 APK 时溢出。",
                    highlights = listOf(catLabel, "$permsCount 项权限", "$stepsCount 个步骤")
                )
                else -> LocalizedPluginCopy(manifest.name, manifest.description, listOf(catLabel, "$permsCount permissions", "$stepsCount steps"))
            }

            else -> LocalizedPluginCopy(
                name = manifest.name,
                description = manifest.description,
                highlights = listOf(catLabel, "$permsCount / 6", "$stepsCount steps")
            )
        }
    }

    fun findingTitle(f: SecurityScanFinding, lang: String): String {
        val code = activeAppLangCode(lang)
        if (code == "en") return f.title
        return when (f.id) {
            "sig_official" -> when (code) {
                "ar" -> "تم التحقق من التوقيع الرسمي ECDSA P-256"
                "es" -> "Firma Oficial ECDSA P-256 Verificada"
                "fr" -> "Signature Officielle ECDSA P-256 Vérifiée"
                "de" -> "Offizielle ECDSA P-256 Signatur verifiziert"
                "zh" -> "官方 ECDSA P-256 签名已验证"
                else -> f.title
            }
            "sig_dev" -> when (code) {
                "ar" -> "توقيع المطور التشفيري صالح"
                "es" -> "Firma Criptográfica de Desarrollador Válida"
                "fr" -> "Signature Cryptographique Développeur Valide"
                "de" -> "Entwickler-Signatur gültig"
                "zh" -> "开发者加密签名有效"
                else -> f.title
            }
            "sig_unsigned" -> when (code) {
                "ar" -> "ملف Manifest غير موقّع (.json)"
                "es" -> "Manifiesto sin firma (.json)"
                "fr" -> "Manifeste non signé (.json)"
                "de" -> "Unsigniertes Manifest (.json)"
                "zh" -> "未签名清单 (.json)"
                else -> f.title
            }
            "semver_compat" -> when (code) {
                "ar" -> "توافق SemVer وواجهة Host API"
                "es" -> "Compatibilidad SemVer y Host API"
                "fr" -> "Compatibilité SemVer & API Hôte"
                "de" -> "SemVer- & Host-API-Kompatibilität"
                "zh" -> "SemVer 与宿主 API 兼容性"
                else -> f.title
            }
            "no_dcl" -> when (code) {
                "ar" -> "التوافق الأمني للعزل (خالي من DEX/SO)"
                "es" -> "Cumplimiento Sandbox (Cero DEX/SO)"
                "fr" -> "Conformité Sandbox (Zéro DEX/SO)"
                "de" -> "Sandbox-Konformität (Kein DEX/SO)"
                "zh" -> "声明式沙盒合规（无 DEX/SO 执行码）"
                else -> f.title
            }
            "perm_audit" -> when (code) {
                "ar" -> "تدقيق نطاق الصلاحيات المطلوبة"
                "es" -> "Auditoría de Alcance de Permisos"
                "fr" -> "Audit des Permissions Demandées"
                "de" -> "Berechtigungs-Scope-Prüfung"
                "zh" -> "权限作用域审计"
                else -> f.title
            }
            "sha_mismatch" -> when (code) {
                "ar" -> "فشل تطابق بصمة SHA-256 (ملف معدل)"
                "es" -> "Resumen SHA-256 Alterado"
                "fr" -> "Empreinte SHA-256 Altérée"
                "de" -> "SHA-256-Prüfsumme manipuliert"
                "zh" -> "SHA-256 校验和不匹配（包被篡改）"
                else -> f.title
            }
            "ecdsa_fail" -> when (code) {
                "ar" -> "فشل التحقق من توقيع ECDSA التشفيري"
                "es" -> "Fallo de Firma Criptográfica ECDSA"
                "fr" -> "Échec Signature Cryptographique ECDSA"
                "de" -> "ECDSA-Signaturprüfung fehlgeschlagen"
                "zh" -> "ECDSA 加密签名校验失败"
                else -> f.title
            }
            "regex_safety" -> when (code) {
                "ar" -> "نمط Regex في SmartMatch غير صالح"
                "es" -> "Patrón Regex SmartMatch Inválido"
                "fr" -> "Expression Regex SmartMatch Invalide"
                "de" -> "Ungültiges SmartMatch-Regex-Muster"
                "zh" -> "SmartMatch 正则表达式异常"
                else -> f.title
            }
            else -> f.title
        }
    }

    fun documentationSections(lang: String): List<LocalizedDocSection> = when (activeAppLangCode(lang)) {
        "ar" -> listOf(
            LocalizedDocSection(
                "1. العقد الموحد للـ Plugin API",
                "كل إضافة تنفّذ العقد القياسي `PakForgePlugin` (`manifest`, `onInitialize(ctx)`, `execute(ctx)`). يوفّر النظام المضيف سياق `PluginHostContext` المقيد بالصلاحيات للوصول إلى العتاد، التشخيص، التقارير، الإشعارات، والأدوات."
            ),
            LocalizedDocSection(
                "2. نظام الإصدارات والـ Manifest (SemVer)",
                "يتحقق المحرك من صيغة `id` (بصيغة reverse-DNS)، و`version`، و`apiVersion` (متوافق مع SDK ${PakForgeSdkSpec.SDK_VERSION})، و`minHostApiLevel` (${PakForgeSdkSpec.HOST_API_LEVEL}). تُرفض أي إضافة غير متوافقة بأمان."
            ),
            LocalizedDocSection(
                "3. حارس الصلاحيات (Zero-Trust Permission Guard)",
                "يمكنك تفعيل أو سحب كل صلاحية مصرح بها في `pakforge-plugin.json` مباشرة من الإعدادات أو قبل التثبيت. أي استدعاء لواجهة بدون إذن يرفع `PluginSecurityException` ويمنع التنفيذ فوراً."
            ),
            LocalizedDocSection(
                "4. حزم متجر .pfpak والتوقيع الرقمي ECDSA P-256",
                "تُحزَم الإضافات كملفات `.pfpak` موقعة رقمياً (`SHA256withECDSA`) وتخضع لفحص التلاعب ومنع تحميل ملفات `.dex`/`.so` العشوائية مما يسمح بتثبيت الأدوات الجديدة دون إعادة بناء البرنامج."
            ),
            LocalizedDocSection(
                "5. واجهات الصلاحيات المرتفعة (Root / Sui / Shizuku)",
                "يقبل PakForge تلقائياً صلاحيات KernelSU و APatch و Magisk (`su` UID 0)، وواجهة Sui، وخدمة Shizuku، ويفحص Dhizuku مع الرجوع الآمن للمثبّت القياسي عند الحاجة."
            )
        )
        "es" -> listOf(
            LocalizedDocSection("1. Contrato Unificado Plugin API", "Cada plugin implementa `PakForgePlugin` (`manifest`, `onInitialize(ctx)`, `execute(ctx)`), recibiendo un `PluginHostContext` aislado para dispositivo, diagnóstico, informes, notificaciones y herramientas."),
            LocalizedDocSection("2. Manifiesto y Versionado SemVer", "Valida `id` reverse-DNS, `version`, `apiVersion` (${PakForgeSdkSpec.SDK_VERSION}) y `minHostApiLevel` (${PakForgeSdkSpec.HOST_API_LEVEL})."),
            LocalizedDocSection("3. Guardia de Permisos Zero-Trust", "Cada permiso declarado se puede aprobar o revocar individualmente; los accesos no autorizados lanzan `PluginSecurityException`."),
            LocalizedDocSection("4. Paquetes .pfpak y Firma ECDSA P-256", "Los paquetes `.pfpak` se verifican con `SHA256withECDSA`, SHA-256 y bloqueo estático DCL (sin `.dex`/`.so`) para instalar herramientas sin recompilar."),
            LocalizedDocSection("5. Motores Privilegiados (Root / Sui / Shizuku)", "Detecta y utiliza automáticamente KernelSU, APatch, Magisk Root, Sui, Shizuku y Dhizuku.")
        )
        "fr" -> listOf(
            LocalizedDocSection("1. Contrat Unifié Plugin API", "Chaque plugin implémente `PakForgePlugin` et reçoit un `PluginHostContext` restreint pour matériel, diagnostics, rapports, notifications et outils."),
            LocalizedDocSection("2. Manifeste & Versionnage SemVer", "Valide `id` reverse-DNS, `version`, `apiVersion` (${PakForgeSdkSpec.SDK_VERSION}) et `minHostApiLevel` (${PakForgeSdkSpec.HOST_API_LEVEL})."),
            LocalizedDocSection("3. Garde des Permissions Zero-Trust", "Chaque permission peut être accordée ou révoquée individuellement avant ou après installation."),
            LocalizedDocSection("4. Paquets .pfpak & Signature ECDSA P-256", "Les paquets `.pfpak` sont vérifiés par `SHA256withECDSA` et scanner anti-DCL (zéro `.dex`/`.so`) sans recompiler l'application."),
            LocalizedDocSection("5. Moteurs Privilégiés (Root / Sui / Shizuku)", "Prend en charge KernelSU, APatch, Magisk, Sui, Shizuku et détecte Dhizuku automatiquement.")
        )
        "de" -> listOf(
            LocalizedDocSection("1. Einheitlicher Plugin-API-Vertrag", "Jedes Plugin implementiert `PakForgePlugin` mit einem abgesicherten `PluginHostContext` für Gerät, Diagnose, Berichte, Benachrichtigungen und Host-Tools."),
            LocalizedDocSection("2. Manifest & SemVer-Versionierung", "Prüft Reverse-DNS-`id`, `version`, `apiVersion` (${PakForgeSdkSpec.SDK_VERSION}) und `minHostApiLevel` (${PakForgeSdkSpec.HOST_API_LEVEL})."),
            LocalizedDocSection("3. Zero-Trust-Berechtigungsschutz", "Berechtigungen lassen sich vor und nach der Installation einzeln gewähren oder entziehen."),
            LocalizedDocSection("4. .pfpak-Pakete & ECDSA P-256 Signatur", "Signierte `.pfpak`-Archive werden per `SHA256withECDSA` und Anti-DCL-Scanner (ohne `.dex`/`.so`) geprüft und ohne Neu-Kompilierung installiert."),
            LocalizedDocSection("5. Privilegierte Backends (Root / Sui / Shizuku)", "Unterstützt KernelSU, APatch, Magisk Root, Sui, Shizuku und erkennt Dhizuku automatisch.")
        )
        "zh" -> listOf(
            LocalizedDocSection("1. 统一插件 API 契约", "所有插件均实现 `PakForgePlugin`，宿主通过受权限控制的 `PluginHostContext` 暴露硬件、诊断、报告、通知和工具接口。"),
            LocalizedDocSection("2. 清单与 SemVer 版本控制", "自动校验反向域名 `id`、`version`、`apiVersion` (${PakForgeSdkSpec.SDK_VERSION}) 与 `minHostApiLevel` (${PakForgeSdkSpec.HOST_API_LEVEL})。"),
            LocalizedDocSection("3. 零信任权限守卫", "安装前和设置中均可逐项授权或撤销权限，越权调用会触发 `PluginSecurityException` 拦截。"),
            LocalizedDocSection("4. .pfpak 签名包与 ECDSA P-256 验证", "插件包使用 `SHA256withECDSA` 签名并经过防篡改与防 `.dex`/`.so` 动态加载扫描，无需重新编译主程序即可扩展工具。"),
            LocalizedDocSection("5. 高权限安装引擎 (Root / Sui / Shizuku)", "自动兼容 KernelSU、APatch、Magisk Root、Sui、Shizuku 并探测 Dhizuku。")
        )
        else -> listOf(
            LocalizedDocSection(
                "1. Unified Plugin API Contract",
                "Every plugin implements `PakForgePlugin` (`manifest`, `onInitialize(ctx)`, `execute(ctx)`). The host supplies a scoped `PluginHostContext` exposing `device`, `diagnostics`, `reports`, `notifications`, and `tools`."
            ),
            LocalizedDocSection(
                "2. Manifest & SemVer Versioning",
                "Manifests validate `id` (reverse-DNS), `version` (SemVer `major.minor.patch`), `apiVersion` (${PakForgeSdkSpec.SDK_VERSION}), and `minHostApiLevel` (${PakForgeSdkSpec.HOST_API_LEVEL}). Incompatible plugins are rejected cleanly."
            ),
            LocalizedDocSection(
                "3. Zero-Trust Permission Guard",
                "Declared permissions in `pakforge-plugin.json` are individually toggleable before install and in Settings. Calling any interface without the user's grant raises `PluginSecurityException` safely."
            ),
            LocalizedDocSection(
                "4. Signed .pfpak Store & ECDSA P-256 Verification",
                "Plugins are packed into ECDSA P-256 signed `.pfpak` bundles and scanned against tampering, ZipSlip, and arbitrary `.dex`/`.so` binary loading so developers can add tools without rebuilding PakForge."
            ),
            LocalizedDocSection(
                "5. Elevated Backends (Root / Sui / Shizuku)",
                "PakForge automatically accepts KernelSU / APatch / Magisk Root (`su` UID 0), Sui (systemless Zygisk Binder), Shizuku v13.1.5, and probes Dhizuku with clean Standard PackageInstaller fallback."
            )
        )
    }

    fun localizeBackendTitle(item: BackendAvailability, lang: String): String {
        val code = activeAppLangCode(lang)
        if (code == "en") return item.title
        return when (item.kind) {
            PrivilegedBackendKind.ROOT_SU -> when (code) {
                "ar" -> item.title.replace("Root Superuser (su)", "صلاحيات الروت (su)").replace("(Root su)", "(روت su)")
                "es" -> item.title.replace("Root Superuser (su)", "Superusuario Root (su)")
                "fr" -> item.title.replace("Root Superuser (su)", "Superutilisateur Root (su)")
                "de" -> item.title.replace("Root Superuser (su)", "Root-Superuser (su)")
                "zh" -> item.title.replace("Root Superuser (su)", "Root 超级用户 (su)")
                else -> item.title
            }
            PrivilegedBackendKind.SUI -> when (code) {
                "ar" -> "جسر Sui المدمج بالنظام (Zygisk)"
                "es" -> "Puente Sui Systemless (Zygisk)"
                "fr" -> "Pont Sui Systemless (Zygisk)"
                "de" -> "Sui (Systemlose Zygisk-Brücke)"
                "zh" -> "Sui (Zygisk 无系统桥接)"
                else -> item.title
            }
            PrivilegedBackendKind.SHIZUKU -> when (code) {
                "ar" -> "خدمة Shizuku (تصحيح لاسلكي / روت)"
                "es" -> "Shizuku (Binder ADB / Root)"
                "fr" -> "Shizuku (Binder ADB / Root)"
                "de" -> "Shizuku (ADB / Root Binder)"
                "zh" -> "Shizuku (ADB / Root 服务)"
                else -> item.title
            }
            PrivilegedBackendKind.DHIZUKU -> when (code) {
                "ar" -> "Dhizuku (صلاحية مالك الجهاز)"
                "es" -> "Dhizuku (Device Owner)"
                "fr" -> "Dhizuku (Propriétaire Appareil)"
                "de" -> "Dhizuku (Geräteeigentümer)"
                "zh" -> "Dhizuku (设备所有者桥接)"
                else -> item.title
            }
            else -> item.title
        }
    }

    fun localizeBackendDetail(raw: String, lang: String): String {
        val code = activeAppLangCode(lang)
        if (code == "en") return raw
        return when {
            raw == "Standard Android PackageInstaller" -> when (code) {
                "ar" -> "مثبّت نظام أندرويد القياسي"
                "es" -> "Instalador estándar de Android"
                "fr" -> "Installateur Android standard"
                "de" -> "Standard-Android-Paketinstaller"
                "zh" -> "Android 标准安装器"
                else -> raw
            }
            raw == "Not rooted" -> when (code) {
                "ar" -> "الجهاز غير مروّت"
                "es" -> "Sin acceso Root"
                "fr" -> "Appareil non rooté"
                "de" -> "Kein Root"
                "zh" -> "未获取 Root 权限"
                else -> raw
            }
            raw == "Sui module not active" -> when (code) {
                "ar" -> "وحدة Sui غير مفعّلة"
                "es" -> "Módulo Sui inactivo"
                "fr" -> "Module Sui inactif"
                "de" -> "Sui-Modul inaktiv"
                "zh" -> "Sui 模块未激活"
                else -> raw
            }
            raw == "Not installed" -> when (code) {
                "ar" -> "غير مثبّت"
                "es" -> "No instalado"
                "fr" -> "Non installé"
                "de" -> "Nicht installiert"
                "zh" -> "未安装"
                else -> raw
            }
            raw.startsWith("Service running") -> when (code) {
                "ar" -> "الخدمة تعمل • يلزم منح الصلاحية"
                "es" -> "Servicio activo • Requiere autorización"
                "fr" -> "Service actif • Autorisation requise"
                "de" -> "Dienst aktiv • Autorisierung nötig"
                "zh" -> "服务运行中 • 需要授权"
                else -> raw
            }
            raw.startsWith("Installed") -> when (code) {
                "ar" -> "مثبّت • شغّل الخدمة عبر التصحيح اللاسلكي"
                "es" -> "Instalado • Iniciar por depuración inalámbrica"
                "fr" -> "Installé • Démarrer via débogage sans fil"
                "de" -> "Installiert • Per Wireless Debugging starten"
                "zh" -> "已安装 • 请通过无线调试启动"
                else -> raw
            }
            raw.contains("Dhizuku not installed") || raw.contains("not installed", ignoreCase = true) -> when (code) {
                "ar" -> "غير مثبّت على الجهاز"
                "es" -> "No instalado en el dispositivo"
                "fr" -> "Non installé sur l'appareil"
                "de" -> "Nicht auf dem Gerät installiert"
                "zh" -> "设备上未安装"
                else -> raw
            }
            else -> raw
        }
    }

    fun localizeSmartReason(reason: String, lang: String): String {
        val code = activeAppLangCode(lang)
        if (code == "en") return reason
        return when {
            reason.endsWith("split APKs detected") -> {
                val n = reason.substringBefore(' ')
                when (code) {
                    "ar" -> "تم اكتشاف $n حزم مقسمة (Split APKs)"
                    "es" -> "$n APKs divididos detectados"
                    "fr" -> "$n splits APK détectés"
                    "de" -> "$n Split-APKs erkannt"
                    "zh" -> "检测到 $n 个拆分 APK"
                    else -> reason
                }
            }
            reason == "sharedUserId detected in manifest" -> when (code) {
                "ar" -> "وجود android:sharedUserId في المانيفست"
                "es" -> "sharedUserId detectado en manifiesto"
                "fr" -> "sharedUserId détecté dans le manifeste"
                "de" -> "sharedUserId im Manifest erkannt"
                "zh" -> "清单中检测到 sharedUserId"
                else -> reason
            }
            reason.endsWith("static library refs") -> {
                val n = reason.substringBefore(' ')
                when (code) {
                    "ar" -> "تعتمد على $n مكتبات ثابتة محمية"
                    "es" -> "$n referencias a librerías estáticas"
                    "fr" -> "$n références de bibliothèques statiques"
                    "de" -> "$n statische Bibliotheksverweise"
                    "zh" -> "包含 $n 个静态库引用"
                    else -> reason
                }
            }
            reason.startsWith("Large archive") -> when (code) {
                "ar" -> reason.replace("Large archive", "حجم حزمة كبير")
                "es" -> reason.replace("Large archive", "Archivo grande")
                "fr" -> reason.replace("Large archive", "Archive volumineuse")
                "de" -> reason.replace("Large archive", "Großes Archiv")
                "zh" -> reason.replace("Large archive", "大型安装包")
                else -> reason
            }
            reason.endsWith("language tables in resources.arsc") -> {
                val n = reason.substringBefore(' ')
                when (code) {
                    "ar" -> "$n جداول لغات داخل resources.arsc"
                    "es" -> "$n tablas de idiomas en resources.arsc"
                    "fr" -> "$n tables de langues dans resources.arsc"
                    "de" -> "$n Sprachtabellen in resources.arsc"
                    "zh" -> "resources.arsc 中包含 $n 种语言表"
                    else -> reason
                }
            }
            reason.endsWith("native ABIs can be trimmed") -> {
                val n = reason.substringBefore(' ')
                when (code) {
                    "ar" -> "$n معماريات معالج يمكن تقليمها"
                    "es" -> "$n ABIs nativas recortables"
                    "fr" -> "$n ABIs natives allégeables"
                    "de" -> "$n native ABIs trimmbar"
                    "zh" -> "可精简 $n 个原生 ABI 架构"
                    else -> reason
                }
            }
            reason.startsWith("Low RAM / Power-Save") -> when (code) {
                "ar" -> reason.replace("Low RAM / Power-Save", "ذاكرة منخفضة / توفير الطاقة").replace("free", "متاحة")
                "es" -> reason.replace("Low RAM / Power-Save", "RAM baja / Ahorro").replace("free", "libre")
                "fr" -> reason.replace("Low RAM / Power-Save", "RAM faible / Économie").replace("free", "libre")
                "de" -> reason.replace("Low RAM / Power-Save", "Wenig RAM / Energiesparen").replace("free", "frei")
                "zh" -> reason.replace("Low RAM / Power-Save", "低内存 / 省电模式").replace("free", "可用")
                else -> reason
            }
            reason.contains("active (UID") -> when (code) {
                "ar" -> reason.replace("active", "نشط")
                "es" -> reason.replace("active", "activo")
                "fr" -> reason.replace("active", "actif")
                "de" -> reason.replace("active", "aktiv")
                "zh" -> reason.replace("active", "已激活")
                else -> reason
            }
            reason == "Security / duplicate audit opportunity" -> when (code) {
                "ar" -> "فرصة لفحص الأمان والملفات المكررة"
                "es" -> "Auditoría de seguridad y duplicados recomendada"
                "fr" -> "Audit sécurité et doublons recommandé"
                "de" -> "Sicherheits- & Duplikat-Prüfung empfohlen"
                "zh" -> "建议进行安全与重复文件审计"
                else -> reason
            }
            reason.startsWith("Device monitor") -> when (code) {
                "ar" -> reason.replace("Device monitor", "مراقب العتاد").replace("free RAM", "رام متاحة")
                "es" -> reason.replace("Device monitor", "Monitor hardware").replace("free RAM", "RAM libre")
                "fr" -> reason.replace("Device monitor", "Moniteur appareil").replace("free RAM", "RAM libre")
                "de" -> reason.replace("Device monitor", "Gerätemonitor").replace("free RAM", "freier RAM")
                "zh" -> reason.replace("Device monitor", "设备监控").replace("free RAM", "可用内存")
                else -> reason
            }
            reason == "Ready for immediate sandbox diagnostics" -> when (code) {
                "ar" -> "جاهز للتشخيص الفوري في بيئة العزل"
                "es" -> "Listo para diagnóstico inmediato en sandbox"
                "fr" -> "Prêt pour diagnostic sandbox immédiat"
                "de" -> "Bereit für sofortige Sandbox-Diagnose"
                "zh" -> "可立即在沙盒中运行诊断"
                else -> reason
            }
            reason.startsWith("Compatible with") -> when (code) {
                "ar" -> reason.replace("Compatible with", "متوافق مع")
                "es" -> reason.replace("Compatible with", "Compatible con")
                "fr" -> reason.replace("Compatible with", "Compatible avec")
                "de" -> reason.replace("Compatible with", "Kompatibel mit")
                "zh" -> reason.replace("Compatible with", "兼容")
                else -> reason
            }
            else -> reason
        }
    }

    fun sdkInterfaces(lang: String): List<LocalizedInterfaceSummary> {
        val code = activeAppLangCode(lang)
        return when (code) {
            "ar" -> listOf(
                LocalizedInterfaceSummary("DeviceInterface", "READ_DEVICE_TELEMETRY", "getDeviceTelemetry(), getRecommendedBufferKb() — قراءة الرام والمعالج والعتاد"),
                LocalizedInterfaceSummary("DiagnosticsInterface", "READ_DIAGNOSTICS", "getPrivilegedBackendsSummary(), getCurrentPackageDiagnostics(), getEngineHealthChecks() — فحص Root/Sui/Shizuku والتوافق"),
                LocalizedInterfaceSummary("ReportsInterface", "READ_REPORTS", "getRecentReports(limit), getTotalSavedBytes(), getLatestAuditJson() — تقارير التوفير والبصمة"),
                LocalizedInterfaceSummary("NotificationInterface", "SEND_NOTIFICATIONS", "postNotification(title, message, severity) — إرسال تنبيهات للنظام"),
                LocalizedInterfaceSummary("HostToolsInterface", "ACCESS_HOST_TOOLS / PRIVILEGED_SHELL_READ", "computeSha256(), runCompatibilitySummary(), applyCompressionPreset(), queryElevatedShellUid() — أدوات الضغط وUID")
            )
            "es" -> listOf(
                LocalizedInterfaceSummary("DeviceInterface", "READ_DEVICE_TELEMETRY", "getDeviceTelemetry(), getRecommendedBufferKb() — RAM, CPU y búfer recomendado"),
                LocalizedInterfaceSummary("DiagnosticsInterface", "READ_DIAGNOSTICS", "getPrivilegedBackendsSummary(), getCurrentPackageDiagnostics(), getEngineHealthChecks() — Estado Root/Sui/Shizuku"),
                LocalizedInterfaceSummary("ReportsInterface", "READ_REPORTS", "getRecentReports(limit), getTotalSavedBytes(), getLatestAuditJson() — Informes y bytes ahorrados"),
                LocalizedInterfaceSummary("NotificationInterface", "SEND_NOTIFICATIONS", "postNotification(title, message, severity) — Notificaciones Android"),
                LocalizedInterfaceSummary("HostToolsInterface", "ACCESS_HOST_TOOLS / PRIVILEGED_SHELL_READ", "computeSha256(), runCompatibilitySummary(), applyCompressionPreset(), queryElevatedShellUid() — Herramientas y UID")
            )
            "fr" -> listOf(
                LocalizedInterfaceSummary("DeviceInterface", "READ_DEVICE_TELEMETRY", "getDeviceTelemetry(), getRecommendedBufferKb() — RAM, CPU et tampon"),
                LocalizedInterfaceSummary("DiagnosticsInterface", "READ_DIAGNOSTICS", "getPrivilegedBackendsSummary(), getCurrentPackageDiagnostics(), getEngineHealthChecks() — État Root/Sui/Shizuku"),
                LocalizedInterfaceSummary("ReportsInterface", "READ_REPORTS", "getRecentReports(limit), getTotalSavedBytes(), getLatestAuditJson() — Rapports et gains"),
                LocalizedInterfaceSummary("NotificationInterface", "SEND_NOTIFICATIONS", "postNotification(title, message, severity) — Alertes système"),
                LocalizedInterfaceSummary("HostToolsInterface", "ACCESS_HOST_TOOLS / PRIVILEGED_SHELL_READ", "computeSha256(), runCompatibilitySummary(), applyCompressionPreset(), queryElevatedShellUid() — Outils hôte & UID")
            )
            "de" -> listOf(
                LocalizedInterfaceSummary("DeviceInterface", "READ_DEVICE_TELEMETRY", "getDeviceTelemetry(), getRecommendedBufferKb() — RAM, CPU & Puffer"),
                LocalizedInterfaceSummary("DiagnosticsInterface", "READ_DIAGNOSTICS", "getPrivilegedBackendsSummary(), getCurrentPackageDiagnostics(), getEngineHealthChecks() — Root/Sui/Shizuku-Status"),
                LocalizedInterfaceSummary("ReportsInterface", "READ_REPORTS", "getRecentReports(limit), getTotalSavedBytes(), getLatestAuditJson() — Berichte & Ersparnis"),
                LocalizedInterfaceSummary("NotificationInterface", "SEND_NOTIFICATIONS", "postNotification(title, message, severity) — Systembenachrichtigungen"),
                LocalizedInterfaceSummary("HostToolsInterface", "ACCESS_HOST_TOOLS / PRIVILEGED_SHELL_READ", "computeSha256(), runCompatibilitySummary(), applyCompressionPreset(), queryElevatedShellUid() — Host-Tools & UID")
            )
            "zh" -> listOf(
                LocalizedInterfaceSummary("DeviceInterface", "READ_DEVICE_TELEMETRY", "getDeviceTelemetry(), getRecommendedBufferKb() — 硬件、可用内存与建议缓冲区"),
                LocalizedInterfaceSummary("DiagnosticsInterface", "READ_DIAGNOSTICS", "getPrivilegedBackendsSummary(), getCurrentPackageDiagnostics(), getEngineHealthChecks() — Root/Sui/Shizuku 与包诊断"),
                LocalizedInterfaceSummary("ReportsInterface", "READ_REPORTS", "getRecentReports(limit), getTotalSavedBytes(), getLatestAuditJson() — 压缩报告与节省统计"),
                LocalizedInterfaceSummary("NotificationInterface", "SEND_NOTIFICATIONS", "postNotification(title, message, severity) — 系统与应用通知"),
                LocalizedInterfaceSummary("HostToolsInterface", "ACCESS_HOST_TOOLS / PRIVILEGED_SHELL_READ", "computeSha256(), runCompatibilitySummary(), applyCompressionPreset(), queryElevatedShellUid() — 宿主工具与 UID")
            )
            else -> listOf(
                LocalizedInterfaceSummary("DeviceInterface", "READ_DEVICE_TELEMETRY", "getDeviceTelemetry(), getRecommendedBufferKb()"),
                LocalizedInterfaceSummary("DiagnosticsInterface", "READ_DIAGNOSTICS", "getPrivilegedBackendsSummary(), getCurrentPackageDiagnostics(), getEngineHealthChecks()"),
                LocalizedInterfaceSummary("ReportsInterface", "READ_REPORTS", "getRecentReports(limit), getTotalSavedBytes(), getLatestAuditJson()"),
                LocalizedInterfaceSummary("NotificationInterface", "SEND_NOTIFICATIONS", "postNotification(title, message, severity)"),
                LocalizedInterfaceSummary("HostToolsInterface", "ACCESS_HOST_TOOLS / PRIVILEGED_SHELL_READ", "computeSha256(), runCompatibilitySummary(), applyCompressionPreset(), queryElevatedShellUid()")
            )
        }
    }

    fun localizeExampleMeta(example: SdkCodeExample, lang: String): Pair<String, String> {
        val code = activeAppLangCode(lang)
        if (code == "en") return example.title to example.subtitle
        return when (example.id) {
            "manifest_json" -> when (code) {
                "ar" -> "1. ملف pakforge-plugin.json (تعريف الإضافة والإصدار)" to "نظام SemVer + الحد الأدنى للـ API + الصلاحيات + قواعد الضغط"
                "es" -> "1. pakforge-plugin.json (Manifiesto y Versionado)" to "SemVer + minHostApiLevel + Permisos + Regla Preestablecida"
                "fr" -> "1. pakforge-plugin.json (Manifeste & Versionnage)" to "SemVer + minHostApiLevel + Permissions + Préréglage"
                "de" -> "1. pakforge-plugin.json (Manifest & Versionierung)" to "SemVer + minHostApiLevel + Berechtigungen + Profil"
                "zh" -> "1. pakforge-plugin.json (插件清单与版本规范)" to "SemVer + minHostApiLevel + 细粒度权限 + 压缩预设"
                else -> example.title to example.subtitle
            }
            "device_diag_example" -> when (code) {
                "ar" -> "2. واجهات العتاد + التشخيص + معرف Shell UID" to "قراءة الرام الحقيقية وحالة KernelSU / Sui / Shizuku"
                "es" -> "2. Interfaces Dispositivo + Diagnóstico + Shell UID" to "Lectura real de SoC/RAM y estado KernelSU/Sui/Shizuku"
                "fr" -> "2. Interfaces Appareil + Diagnostics + Shell UID" to "Lecture réelle SoC/RAM et état KernelSU/Sui/Shizuku"
                "de" -> "2. Geräte- + Diagnose- + Shell-UID-Schnittstellen" to "Echte SoC/RAM-Telemetrie und KernelSU/Sui/Shizuku-Status"
                "zh" -> "2. 设备遥测 + 诊断 + Shell UID 接口示例" to "读取真实硬件内存及 KernelSU / Sui / Shizuku 状态"
                else -> example.title to example.subtitle
            }
            "reports_tools_example" -> when (code) {
                "ar" -> "3. واجهة التقارير + الأدوات (محمية بالصلاحيات)" to "التحقق من hasPermission() قبل طلب التقارير وحساب SHA-256"
                "es" -> "3. Informes + Herramientas Host (Con Permisos)" to "Validación hasPermission() antes de consultar informes y SHA-256"
                "fr" -> "3. Rapports + Outils Hôte (Protégés par Permission)" to "Vérification hasPermission() avant accès aux rapports et SHA-256"
                "de" -> "3. Berichte + Host-Tools (Berechtigungsgeschützt)" to "Prüft hasPermission() vor Berichtsabruf und SHA-256-Berechnung"
                "zh" -> "3. 报告与宿主工具接口（权限保护）" to "访问历史报告与 SHA-256 前强制检查 hasPermission()"
                else -> example.title to example.subtitle
            }
            else -> example.title to example.subtitle
        }
    }
}
