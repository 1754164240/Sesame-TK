package fansirsqi.xposed.sesame.task.antMember

enum class YebExpGoldStepResult {
    NOT_NEEDED,
    CONFIRMED,
    RETRY,
    DEFERRED
}

data class YebExpGoldRunResult(
    val signIn: YebExpGoldStepResult,
    val voucher: YebExpGoldStepResult
) {
    val retryable: Boolean
        get() = signIn == YebExpGoldStepResult.RETRY ||
            voucher == YebExpGoldStepResult.RETRY
}

class YebExpGoldWorkflow(
    private val queryMain: suspend () -> String,
    private val signIn: suspend () -> String,
    private val queryVouchers: suspend () -> String,
    private val convertVouchers: suspend () -> String
) {

    suspend fun run(): YebExpGoldRunResult {
        val initialMain = runCatching { queryMain() }.getOrNull()
            ?: return retrySignResult()
        val initialSignState = YebExpGoldPolicy.signState(initialMain)
        val signResult = when (initialSignState) {
            YebExpGoldSignState.UNKNOWN ->
                return retrySignResult()

            YebExpGoldSignState.SIGNED ->
                YebExpGoldStepResult.NOT_NEEDED

            YebExpGoldSignState.PENDING -> {
                val actionResponse = runCatching { signIn() }.getOrNull()
                if (
                    actionResponse == null ||
                    !YebExpGoldPolicy.isActionAccepted(actionResponse)
                ) {
                    return retrySignResult()
                }
                val refreshedMain = runCatching { queryMain() }.getOrNull()
                if (
                    refreshedMain != null &&
                    YebExpGoldPolicy.signState(refreshedMain) ==
                    YebExpGoldSignState.SIGNED
                ) {
                    YebExpGoldStepResult.CONFIRMED
                } else {
                    return retrySignResult()
                }
            }
        }

        val initialVoucherResponse = runCatching { queryVouchers() }.getOrNull()
        val initialPendingCount = initialVoucherResponse
            ?.let(YebExpGoldPolicy::pendingVoucherCount)
            ?: return YebExpGoldRunResult(
                signIn = signResult,
                voucher = YebExpGoldStepResult.RETRY
            )
        if (initialPendingCount <= 0) {
            return YebExpGoldRunResult(
                signIn = signResult,
                voucher = YebExpGoldStepResult.NOT_NEEDED
            )
        }

        val convertResponse = runCatching { convertVouchers() }.getOrNull()
        if (
            convertResponse == null ||
            !YebExpGoldPolicy.isActionAccepted(convertResponse)
        ) {
            return YebExpGoldRunResult(
                signIn = signResult,
                voucher = YebExpGoldStepResult.RETRY
            )
        }
        val refreshedVoucherResponse = runCatching {
            queryVouchers()
        }.getOrNull()
        val remainingCount = refreshedVoucherResponse
            ?.let(YebExpGoldPolicy::pendingVoucherCount)
        val voucherResult = if (
            remainingCount != null &&
            remainingCount < initialPendingCount
        ) {
            YebExpGoldStepResult.CONFIRMED
        } else {
            YebExpGoldStepResult.RETRY
        }
        return YebExpGoldRunResult(
            signIn = signResult,
            voucher = voucherResult
        )
    }

    private fun retrySignResult(): YebExpGoldRunResult =
        YebExpGoldRunResult(
            signIn = YebExpGoldStepResult.RETRY,
            voucher = YebExpGoldStepResult.DEFERRED
        )
}
