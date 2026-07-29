package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.task.youthPrivilege.YouthPrivilege

object Privilege {
    fun youthPrivilege(): Boolean {
        return YouthPrivilege.claimForestPropsFromForest()
    }

    fun studentSignInRedEnvelope() {
        YouthPrivilege.checkInFromForest()
    }
}
