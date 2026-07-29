package fansirsqi.xposed.sesame.task.antMember

class GoldTicketWorkflow(
    private val queryHome: () -> String,
    private val triggerSign: () -> String,
    private val queryBalance: () -> String
) {

    fun signIn(): GoldTicketOutcome {
        val before = GoldTicketPolicy.parseSign(queryHome())
        if (!before.recognized) {
            return GoldTicketOutcome.RETRY
        }
        if (before.signed) {
            return GoldTicketOutcome.NO_ACTION
        }
        if (!GoldTicketPolicy.isActionAccepted(triggerSign())) {
            return GoldTicketOutcome.RETRY
        }
        val after = GoldTicketPolicy.parseSign(queryHome())
        return if (after.recognized && after.signed) {
            GoldTicketOutcome.CONFIRMED
        } else {
            GoldTicketOutcome.RETRY
        }
    }

    fun readBalance(): GoldTicketBalanceSnapshot {
        return GoldTicketPolicy.parseBalance(queryBalance())
    }
}
