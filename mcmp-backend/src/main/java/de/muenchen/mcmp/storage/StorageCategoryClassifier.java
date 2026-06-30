package de.muenchen.mcmp.storage;

import java.util.regex.Pattern;

public final class StorageCategoryClassifier {

    // NFS volume patterns — matched against mountPathNfs (format: svmName:mountPath)
    private static final Pattern NFS_STANDARD_SHARE = Pattern.compile(
            "svm[pkc]\\d{2}dcn\\.srv\\.muenchen\\.de:/sn3_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NFS_CLONE = Pattern.compile(
            "svm[pkc]\\d{2}dcn\\.srv\\.muenchen\\.de:/sn3c_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NFS_WORM = Pattern.compile(
            "svm[pkc]\\d{2}dcn\\.srv\\.muenchen\\.de:/wn3_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORACLE_VOLUME = Pattern.compile(
            "svm\\d{2}(dcn|odb)\\.srv\\.muenchen\\.de:/odb_[a-z0-9]{3,20}",
            Pattern.CASE_INSENSITIVE);

    // CIFS share patterns — matched against mountPathCifs (flexible prefix)
    private static final Pattern CIFS_STANDARD_SHARE = Pattern.compile(
            ".*dcc\\.srv\\.muenchen\\.de[/\\\\]sc_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CIFS_CLONE = Pattern.compile(
            ".*dcc\\.srv\\.muenchen\\.de[/\\\\]scc_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CIFS_WORM = Pattern.compile(
            ".*dcc\\.srv\\.muenchen\\.de[/\\\\]wc_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}$",
            Pattern.CASE_INSENSITIVE);

    // Qtree pattern — matched against qtree's own mountPathNfs
    private static final Pattern ORACLE_FRA_QTREE = Pattern.compile(
            "svm\\d{2}(dcn|odb)\\.srv\\.muenchen\\.de:/odb_fra_\\d{4}/fra_[a-z0-9]{3,20}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NFS_SHARED = Pattern.compile(
            "svm\\d{2}dcn\\.srv\\.muenchen\\.de:/share_[a-z0-9]{1,30}/[a-z0-9]{1,30}_\\d{2}",
            Pattern.CASE_INSENSITIVE);

    // S3 bucket name pattern
    private static final Pattern S3_SERVICE_BUCKET = Pattern.compile(
            "[ei][nx]t-[a-z0-9]{3,20}[pkclt]-[a-z0-9]{3,20}(-(ec|6cto2c))?",
            Pattern.CASE_INSENSITIVE);

    private StorageCategoryClassifier() {}

    /** Classify NFS volume. mountPathNfs already contains the full path including SVM prefix. */
    public static StorageCategory classifyNfs(String mountPathNfs) {
        if (mountPathNfs == null) return null;
        if (NFS_STANDARD_SHARE.matcher(mountPathNfs).matches()) return StorageCategory.NFS_STANDARD_SHARE;
        if (NFS_CLONE.matcher(mountPathNfs).matches())          return StorageCategory.NFS_CLONE;
        if (NFS_WORM.matcher(mountPathNfs).matches())           return StorageCategory.NFS_WORM;
        if (ORACLE_VOLUME.matcher(mountPathNfs).matches())      return StorageCategory.ORACLE_VOLUME;
        return null;
    }

    /** Classify CIFS share from its mountPathCifs. */
    public static StorageCategory classifyCifs(String mountPathCifs) {
        if (mountPathCifs == null) return null;
        if (CIFS_STANDARD_SHARE.matcher(mountPathCifs).matches()) return StorageCategory.CIFS_STANDARD_SHARE;
        if (CIFS_CLONE.matcher(mountPathCifs).matches())           return StorageCategory.CIFS_CLONE;
        if (CIFS_WORM.matcher(mountPathCifs).matches())            return StorageCategory.CIFS_WORM;
        return null;
    }

    /** Classify qtree. mountPathNfs is the qtree's own mount path (already contains full path). */
    public static StorageCategory classifyQtree(String mountPathNfs) {
        if (mountPathNfs == null) return null;
        if (ORACLE_FRA_QTREE.matcher(mountPathNfs).matches()) return StorageCategory.ORACLE_FRA_QTREE;
        if (NFS_SHARED.matcher(mountPathNfs).matches())         return StorageCategory.NFS_SHARED;
        return null;
    }

    /** Classify S3 bucket by bucket name. */
    public static StorageCategory classifyS3(String bucketName) {
        if (bucketName == null) return null;
        if (S3_SERVICE_BUCKET.matcher(bucketName).matches()) return StorageCategory.S3_SERVICE_BUCKET;
        return null;
    }
}
