package fansirsqi.xposed.sesame.ui

import fansirsqi.xposed.sesame.ui.model.UiMode
import org.junit.Assert.assertEquals
import org.junit.Test

class UiModeDefaultTest {

    @Test
    fun `缺失空白或未知配置默认使用第二版界面`() {
        assertEquals(UiMode.Web, UiMode.fromValue(null))
        assertEquals(UiMode.Web, UiMode.fromValue(""))
        assertEquals(UiMode.Web, UiMode.fromValue("unknown"))
    }

    @Test
    fun `保留用户已选择的新版原生界面`() {
        assertEquals(UiMode.New, UiMode.fromValue("new"))
    }
}
