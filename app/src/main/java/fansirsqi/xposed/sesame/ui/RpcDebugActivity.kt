package fansirsqi.xposed.sesame.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.android.material.button.MaterialButton
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.entity.RpcDebugItem
import fansirsqi.xposed.sesame.ui.log.LogViewerComposeActivity
import fansirsqi.xposed.sesame.ui.widget.RpcDebugAdapter
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RPC 调试 Activity
 * 支持持久化、列表管理、备份恢复等功能
 */
class RpcDebugActivity : BaseActivity() {

    private val TAG = "RpcDebugActivity"
    private val PREFS_NAME = "rpc_debug_prefs"
    private val KEY_ITEMS = "rpc_debug_items"
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyHint: View
    private lateinit var btnAdd: MaterialButton
    private lateinit var btnBackup: MaterialButton
    private lateinit var btnRestore: MaterialButton
    private lateinit var btnLoadDefault: MaterialButton
    
    private lateinit var adapter: RpcDebugAdapter
    private val items = mutableListOf<RpcDebugItem>()
    private val objectMapper = ObjectMapper()
    private lateinit var sharedPreferences: SharedPreferences
    
    // JSON 模板常量
    private val JSON_TEMPLATE = """
{
  "Name": "",
  "Method": "alipay.xxx.xxx",
  "requestData": [{}]
}
    """.trimIndent()
    
    // 支持的字段名说明
    // Name/name - 功能名称（可选）
    // Method/method/methodName - RPC 方法名（必填）
    // requestData/RequestData - 请求数据（必填）

    /**
     * 获取默认的 RPC 调试项列表
     */
    private fun getDefaultItems(): List<RpcDebugItem> {
        return listOf(
            RpcDebugItem(
                name = "雇佣黄金鸡",
                method = "com.alipay.antfarm.hireAnimal",
                requestData = listOf(
                    mapOf(
                        "hireActionType" to "HIRE_IN_SELF_FARM",
                        "hireAnimalId" to "20250725105101013088000000000004",
                        "isNpcAnimal" to true,
                        "requestType" to "NORMAL",
                        "sceneCode" to "ANTFARM",
                        "source" to "licaixiaoji_2025_1",
                        "version" to "1.8.2302070202.46"
                    )
                )
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rpc_debug)
        baseTitle = "RPC 调试"
        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        initViews()
        loadItems()
        
        // 如果是首次使用，加载默认列表
        if (items.isEmpty() && !sharedPreferences.contains(KEY_ITEMS)) {
            loadDefaultItems()
        }
        
        updateEmptyState()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_view_rpc_debug)
        tvEmptyHint = findViewById(R.id.tv_empty_hint)
        btnAdd = findViewById(R.id.btn_add)
        btnBackup = findViewById(R.id.btn_backup)
        btnRestore = findViewById(R.id.btn_restore)
        btnLoadDefault = findViewById(R.id.btn_load_default)
        
        // 设置 RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RpcDebugAdapter(
            items = items,
            onRun = { item -> runRpcItem(item) },
            onEdit = { item -> showEditDialog(item) },
            onDelete = { item -> deleteItem(item) },
            onCopy = { item -> shareItem(item) }  // 改为分享
        )
        recyclerView.adapter = adapter
        
        // 按钮点击事件
        btnAdd.setOnClickListener { showAddDialog() }  // 自动读取剪贴板
        btnBackup.setOnClickListener { backupItems() }
        btnRestore.setOnClickListener { restoreItems() }
        btnLoadDefault.setOnClickListener { loadDefaultItems() }
    }

    /**
     * 从 SharedPreferences 加载数据
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun loadItems() {
        try {
            val jsonString = sharedPreferences.getString(KEY_ITEMS, null)
            if (jsonString != null) {
                val loadedItems = objectMapper.readValue(
                    jsonString,
                    object : TypeReference<MutableList<RpcDebugItem>>() {}
                )
                items.clear()
                items.addAll(loadedItems)
                adapter.notifyDataSetChanged()
                Log.d(TAG, "加载了 ${items.size} 个 RPC 调试项")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载数据失败: ${e.message}")
        }
    }

    /**
     * 加载默认列表
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun loadDefaultItems() {
        try {
            val defaultItems = getDefaultItems()
            
            // 检查是否已存在相同的项（根据 method 判断）
            val existingMethods = items.map { it.method }.toSet()
            val newItems = defaultItems.filter { it.method !in existingMethods }
            
            if (newItems.isEmpty()) {
                ToastUtil.showToast(this, "默认列表已存在，无需重复加载")
                return
            }
            
            items.addAll(newItems)
            saveItems()
            adapter.notifyDataSetChanged()
            updateEmptyState()
            
            Log.d(TAG, "已加载 ${newItems.size} 个默认 RPC 调试项")
            ToastUtil.showToast(this, "已加载 ${newItems.size} 个默认调试项")
        } catch (e: Exception) {
            Log.e(TAG, "加载默认列表失败: ${e.message}")
            ToastUtil.showToast(this, "加载失败: ${e.message}")
        }
    }

    /**
     * 保存数据到 SharedPreferences
     */
    private fun saveItems() {
        try {
            val jsonString = objectMapper.writeValueAsString(items)
            sharedPreferences.edit { putString(KEY_ITEMS, jsonString) }
            Log.d(TAG, "保存了 ${items.size} 个 RPC 调试项")
        } catch (e: Exception) {
            Log.e(TAG, "保存数据失败: ${e.message}")
            ToastUtil.showToast(this, "保存失败: ${e.message}")
        }
    }

    /**
     * 更新空状态显示
     */
    private fun updateEmptyState() {
        if (items.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvEmptyHint.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmptyHint.visibility = View.GONE
        }
    }

    /**
     * 获取剪贴板内容
     */
    private fun getClipboardText(): String {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }

    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * 清理 JSON 字符串中的不可见字符
     */
    private fun cleanJsonString(jsonText: String): String {
        return jsonText
            .replace('\u00A0', ' ')  // 替换不间断空格 (code 160)
            .replace('\u2007', ' ')  // 替换数字空格
            .replace('\u202F', ' ')  // 替换窄不间断空格
            .trim()
    }

    /**
     * 解析 JSON 并提取字段（支持多种格式）
     * 支持格式1: { "Method": "xxx", "requestData": [...] }
     * 支持格式2: { "Method": "xxx", "Params": { "requestData": [...] } }
     */
    private fun parseJsonFields(jsonText: String): Triple<String, String, Any?> {
        val cleanedJson = cleanJsonString(jsonText)
        val jsonMap = objectMapper.readValue(cleanedJson, Map::class.java)
        
        // 提取 Method
        var method = (jsonMap["Method"] ?: jsonMap["method"] ?: jsonMap["methodName"])?.toString() ?: ""
        
        // 提取 requestData（支持两种格式）
        var requestData: Any? = jsonMap["requestData"] ?: jsonMap["RequestData"]
        
        // 如果有 Params 字段，从 Params 中提取
        val params = jsonMap["Params"] ?: jsonMap["params"]
        if (params is Map<*, *>) {
            // 如果 Params 中有 operationType，优先使用它作为 method
            val operationType = params["operationType"]?.toString()
            if (!operationType.isNullOrEmpty()) {
                method = operationType
            }
            // 从 Params 中提取 requestData
            val paramsRequestData = params["requestData"] ?: params["RequestData"]
            if (paramsRequestData != null) {
                requestData = paramsRequestData
            }
        }
        
        // 提取名称
        val name = (jsonMap["Name"] ?: jsonMap["name"])?.toString() ?: ""
        
        return Triple(name, method, requestData)
    }

    /**
     * 将 RpcDebugItem 转换为 JSON 字符串
     */
    private fun itemToJson(item: RpcDebugItem): String {
        val jsonMap = mapOf(
            "Name" to item.name,
            "Method" to item.method,
            "requestData" to item.requestData
        )
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMap)
    }

    /**
     * 准备 JSON 输入框内容（从剪贴板或使用模板）
     */
    private fun prepareJsonInput(): String {
        val clipText = getClipboardText()
        
        if (clipText.isEmpty() || (!clipText.trim().startsWith("{") && !clipText.trim().startsWith("["))) {
            return JSON_TEMPLATE
        }
        
        return try {
            val cleanedText = cleanJsonString(clipText)
            // 如果是数组，取第一个
            if (cleanedText.startsWith("[")) {
                val list = objectMapper.readValue(cleanedText, List::class.java)
                if (list.isNotEmpty()) {
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list[0])
                } else {
                    JSON_TEMPLATE
                }
            } else {
                // 格式化 JSON 使其更易读
                val jsonMap = objectMapper.readValue(cleanedText, Map::class.java)
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析剪贴板 JSON 失败: ${e.message}")
            JSON_TEMPLATE
        }
    }

    /**
     * 处理对话框保存逻辑
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun handleDialogSave(
        name: String,
        jsonText: String,
        existingItem: RpcDebugItem? = null,
        dialog: AlertDialog
    ) {
        if (jsonText.isEmpty()) {
            ToastUtil.showToast(this, "JSON 数据不能为空")
            return
        }

        try {
            val (parsedName, method, requestData) = parseJsonFields(jsonText)
            val finalName = name.ifEmpty { parsedName }

            if (method.isEmpty()) {
                ToastUtil.showToast(this, "Method 不能为空")
                return
            }

            if (existingItem != null) {
                // 编辑模式
                existingItem.name = finalName
                existingItem.method = method
                existingItem.requestData = requestData
                ToastUtil.showToast(this, "保存成功")
            } else {
                // 新建模式
                val newItem = RpcDebugItem(
                    name = finalName,
                    method = method,
                    requestData = requestData
                )
                items.add(newItem)
                ToastUtil.showToast(this, "添加成功")
            }

            saveItems()
            adapter.notifyDataSetChanged()
            updateEmptyState()
            dialog.dismiss()
        } catch (e: Exception) {
            Log.e(TAG, "解析 JSON 失败: ${e.message}")
            ToastUtil.showToast(this, "JSON 格式错误: ${e.message}")
        }
    }

    /**
     * 显示添加对话框（自动读取剪贴板）
     */
    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rpc_edit, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etJson = dialogView.findViewById<EditText>(R.id.et_json)
        
        // 自动填充剪贴板内容或模板
        etJson.setText(prepareJsonInput())
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("添加 RPC 调试项")
            .setView(dialogView)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .create()
        
        dialog.show()
        
        // 手动设置保存按钮点击事件
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            val jsonText = etJson.text?.toString()?.trim() ?: ""
            handleDialogSave(name, jsonText, null, dialog)
        }
    }

    /**
     * 显示编辑对话框
     */
    private fun showEditDialog(item: RpcDebugItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rpc_edit, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etJson = dialogView.findViewById<EditText>(R.id.et_json)

        // 填充现有数据
        etName.setText(item.name)
        etJson.setText(itemToJson(item))

        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑 RPC 调试项")
            .setView(dialogView)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .create()
        
        dialog.show()
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            val jsonText = etJson.text?.toString()?.trim() ?: ""
            handleDialogSave(name, jsonText, item, dialog)
        }
    }

    /**
     * 运行 RPC 调试项
     */
    private fun runRpcItem(item: RpcDebugItem) {
        lifecycleScope.launch {
            try {
                // 清空日志文件
                val logFile = Files.getDebugLogFile()
                Files.clearFile(logFile)
                // 延迟等待文件系统同步
                delay(300)
                // 发送 RPC 请求
                val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest")
                intent.putExtra("method", item.method)
                intent.putExtra("data", item.getRequestDataString())
                intent.putExtra("type", "Rpc")
                sendBroadcast(intent)
                Log.d(TAG, "发送 RPC 请求: ${item.getDisplayName()}")
                ToastUtil.showToast(this@RpcDebugActivity, "已发送: ${item.getDisplayName()}")
                // 跳转到日志查看器
                try {
                    val logIntent = Intent(this@RpcDebugActivity, LogViewerComposeActivity::class.java).apply {
                        data = logFile.toUri()
                    }
                    startActivity(logIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "无法打开日志查看器: ${e.message}")
                    ToastUtil.showToast(this@RpcDebugActivity, "无法打开日志查看器")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送 RPC 请求失败: ${e.message}")
                ToastUtil.showToast(this@RpcDebugActivity, "发送失败: ${e.message}")
            }
        }
    }

    /**
     * 分享项（复制单个项到剪贴板，用于添加）
     */
    private fun shareItem(item: RpcDebugItem) {
        try {
            val jsonText = itemToJson(item)
            copyToClipboard("RPC Debug Item", jsonText)
            ToastUtil.showToast(this, "已复制，可在其他设备点击添加导入")
        } catch (e: Exception) {
            Log.e(TAG, "分享失败: ${e.message}")
            ToastUtil.showToast(this, "分享失败: ${e.message}")
        }
    }

    /**
     * 备份所有数据（导出数组）
     */
    private fun backupItems() {
        if (items.isEmpty()) {
            ToastUtil.showToast(this, "没有数据可备份")
            return
        }
        
        try {
            val jsonText = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(items)
            copyToClipboard("RPC Debug Backup", jsonText)
            ToastUtil.showToast(this, "已备份 ${items.size} 项到剪贴板")
        } catch (e: Exception) {
            Log.e(TAG, "备份失败: ${e.message}")
            ToastUtil.showToast(this, "备份失败: ${e.message}")
        }
    }

    /**
     * 恢复数据（导入数组）
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun restoreItems() {
        val clipText = getClipboardText()
        
        if (clipText.isEmpty()) {
            ToastUtil.showToast(this, "剪贴板为空")
            return
        }
        
        // 只接受数组格式
        if (!clipText.trim().startsWith("[")) {
            ToastUtil.showToast(this, "恢复失败：只支持数组格式备份")
            return
        }
    
        try {
            Log.d(TAG, "开始恢复，JSON 长度: ${clipText.length}")
        
            val importedItems = objectMapper.readValue(clipText, object : TypeReference<List<RpcDebugItem>>() {})

            Log.d(TAG, "解析成功，共 ${importedItems.size} 项")

            // 确认对话框
            AlertDialog.Builder(this)
                .setTitle("确认恢复")
                .setMessage("将恢复 ${importedItems.size} 项数据\n当前数据将被清空")
                .setPositiveButton("恢复") { _, _ ->
                    items.clear()
                    items.addAll(importedItems)
                    
                    // 为没有 id 的项生成 id
                    items.forEach { item ->
                        if (item.id.isEmpty()) {
                            item.id = System.currentTimeMillis().toString()
                        }
                    }
                    
                    saveItems()
                    adapter.notifyDataSetChanged()
                    updateEmptyState()

                    Log.d(TAG, "恢复完成，当前总数: ${items.size}")
                    ToastUtil.showToast(this, "恢复成功：${importedItems.size} 项")
                }
                .setNegativeButton("取消", null)
                .show()
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复失败: ${e.message}", e)
            ToastUtil.showToast(this, "恢复失败: ${e.message}")
        }
    }

    /**
     * 删除项
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun deleteItem(item: RpcDebugItem) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除 \"${item.getDisplayName()}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                items.remove(item)
                saveItems()
                adapter.notifyDataSetChanged()
                updateEmptyState()
                ToastUtil.showToast(this, "已删除")
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
