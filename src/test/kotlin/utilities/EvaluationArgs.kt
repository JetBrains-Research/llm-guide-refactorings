package utilities

import org.bson.conversions.Bson

enum class RepoType {
    ONLINE_GITHUB,
    LOCAL_GIT_CLONE,
    LOCAL_FILE
}

data class RepoArgs(
    val repoPath: String = "",
    val repoBranch: String = "",
    val repoType: RepoType
)

data class MongoArgs(
    val db: String,
    val updateDocs: Boolean,
    val mongoFilter: Bson,
    val mongoDocsFetchLimit: Int,
)

data class LLMArgs(
    val maxShots: Int,
    val temperature: Double = 0.0,
)
