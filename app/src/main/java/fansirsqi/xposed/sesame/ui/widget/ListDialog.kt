package fansirsqi.xposed.sesame.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.entity.AreaCode
import fansirsqi.xposed.sesame.entity.CooperateEntity
import fansirsqi.xposed.sesame.entity.MapperEntity
import fansirsqi.xposed.sesame.model.SelectModelFieldFunc
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectAndCountModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectAndCountOneModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectOneModelField
import fansirsqi.xposed.sesame.ui.OptionsAdapter
import fansirsqi.xposed.sesame.util.maps.CooperateMap
import org.json.JSONException

@SuppressLint("StaticFieldLeak")
object ListDialog {
    private var listDialog: AlertDialog? = null

    @SuppressLint("StaticFieldLeak")
    private var btnFindLast: Button? = null

    @SuppressLint("StaticFieldLeak")
    private var btnFindNext: Button? = null

    @SuppressLint("StaticFieldLeak")
    private var btnSelectAll: Button? = null

    @SuppressLint("StaticFieldLeak")
    private var btnSelectInvert: Button? = null

    @SuppressLint("StaticFieldLeak")
    private var searchText: EditText? = null

    @SuppressLint("StaticFieldLeak")
    private var lvList: ListView? = null

    private var selectModelFieldFunc: SelectModelFieldFunc? = null
    private var hasCount: Boolean = false
    private var listType: ListType? = null

    @SuppressLint("StaticFieldLeak")
    private var layoutBatchProcess: RelativeLayout? = null

    enum class ListType {
        RADIO, CHECK, SHOW
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, selectModelField: SelectOneModelField, listType: ListType) {
        show(c, title, selectModelField.expandValue, selectModelField, false, listType)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, selectModelField: SelectAndCountOneModelField, listType: ListType) {
        show(c, title, selectModelField.expandValue, selectModelField, false, listType)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun show(c: Context, title: CharSequence, selectModelField: SelectModelField) {
        show(c, title, selectModelField, ListType.CHECK)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, selectModelField: SelectAndCountModelField) {
        show(c, title, selectModelField, ListType.CHECK)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun show(c: Context, title: CharSequence, selectModelField: SelectModelField, listType: ListType) {
        show(c, title, selectModelField.expandValue, selectModelField, false, listType)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, selectModelField: SelectAndCountModelField, listType: ListType) {
        show(c, title, selectModelField.expandValue, selectModelField, true, listType)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, bl: List<MapperEntity>, selectModelFieldFunc: SelectModelFieldFunc, hasCount: Boolean) {
        show(c, title, bl, selectModelFieldFunc, hasCount, ListType.CHECK)
    }

    @JvmStatic
    fun show(c: Context, title: CharSequence, bl: List<MapperEntity>, selectModelFieldFunc: SelectModelFieldFunc, hasCount: Boolean, listType: ListType) {
        this.selectModelFieldFunc = selectModelFieldFunc
        this.hasCount = hasCount
        val la = ListAdapter.getClear(c, listType)
        la.setBaseList(bl)
        la.setSelectedList(selectModelFieldFunc)
        showListDialog(c, title)
        this.listType = listType
    }

    private fun showListDialog(c: Context, title: CharSequence) {
        if (listDialog == null || listDialog?.context != c) {
            listDialog = MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setView(getListView(c))
                .setPositiveButton(c.getString(R.string.close), null)
                .create()
        }
        listDialog?.setOnShowListener { p1 ->
            val d = p1 as AlertDialog
            layoutBatchProcess = d.findViewById(R.id.layout_batch_process)
            layoutBatchProcess?.visibility =
                if (listType == ListType.CHECK && !hasCount) View.VISIBLE else View.GONE
            ListAdapter.get(c).notifyDataSetChanged()
        }
        listDialog?.show()
        val positiveButton = listDialog?.getButton(DialogInterface.BUTTON_POSITIVE)
        positiveButton?.setTextColor(ContextCompat.getColor(c, R.color.selection_color))
    }

    @SuppressLint("InflateParams")
    private fun getListView(c: Context): View {
        val v = LayoutInflater.from(c).inflate(R.layout.dialog_list, null)

        btnFindLast = v.findViewById(R.id.btn_find_last)
        btnFindNext = v.findViewById(R.id.btn_find_next)
        btnSelectAll = v.findViewById(R.id.btn_select_all)
        btnSelectInvert = v.findViewById(R.id.btn_select_invert)

        val onBtnClickListener = View.OnClickListener { v1 ->
            if ((searchText?.length() ?: 0) <= 0) return@OnClickListener
            val la = ListAdapter.get(v1.context)
            val index = when (v1.id) {
                R.id.btn_find_last -> la.findLast(searchText!!.text.toString())
                R.id.btn_find_next -> la.findNext(searchText!!.text.toString())
                else -> -1
            }
            if (index < 0) {
                Toast.makeText(v1.context, "未搜到", Toast.LENGTH_SHORT).show()
            } else {
                lvList?.setSelection(index)
            }
        }
        btnFindLast?.setOnClickListener(onBtnClickListener)
        btnFindNext?.setOnClickListener(onBtnClickListener)

        val batchBtnOnClickListener = View.OnClickListener { v1 ->
            val la = ListAdapter.get(v1.context)
            when (v1.id) {
                R.id.btn_select_all -> la.selectAll()
                R.id.btn_select_invert -> la.selectInvert()
            }
        }
        btnSelectAll?.setOnClickListener(batchBtnOnClickListener)
        btnSelectInvert?.setOnClickListener(batchBtnOnClickListener)

        searchText = v.findViewById(R.id.edt_find)
        lvList = v.findViewById(R.id.lv_list)
        lvList?.adapter = ListAdapter.getClear(c)

        lvList?.setOnItemClickListener { parent, view, position, _ ->
            if (listType == ListType.SHOW) return@setOnItemClickListener
            val cur = parent.adapter.getItem(position) as MapperEntity
            val holder = view.tag as ListAdapter.ViewHolder
            if (!hasCount) {
                if (listType == ListType.RADIO) {
                    selectModelFieldFunc?.clear()
                    if (holder.cb.isChecked) {
                        holder.cb.isChecked = false
                    } else {
                        for (vh in ListAdapter.viewHolderList) vh.cb.isChecked = false
                        holder.cb.isChecked = true
                        selectModelFieldFunc?.add(cur.id, 0)
                    }
                } else {
                    if (holder.cb.isChecked) {
                        selectModelFieldFunc?.remove(cur.id)
                        holder.cb.isChecked = false
                    } else {
                        if (selectModelFieldFunc?.contains(cur.id) == false) {
                            selectModelFieldFunc?.add(cur.id, 0)
                        }
                        holder.cb.isChecked = true
                    }
                }
            } else {
                val edt = EditText(c)
                val edtDialog = MaterialAlertDialogBuilder(c)
                    .setTitle(cur.name)
                    .setView(edt)
                    .setPositiveButton(c.getString(R.string.ok)) { _, _ ->
                        if (edt.length() > 0) {
                            try {
                                val count = edt.text.toString().toInt()
                                if (count > 0) {
                                    selectModelFieldFunc?.add(cur.id, count)
                                    holder.cb.isChecked = true
                                } else {
                                    selectModelFieldFunc?.remove(cur.id)
                                    holder.cb.isChecked = false
                                }
                            } catch (_: Exception) {
                            }
                        }
                        ListAdapter.get(c).notifyDataSetChanged()
                    }
                    .setNegativeButton(c.getString(R.string.cancel), null)
                    .create()
                edt.hint = if (cur is CooperateEntity) "浇水克数" else "次数"
                val value = selectModelFieldFunc?.get(cur.id)
                if (value != null && value >= 0) edt.setText(value.toString())
                edtDialog.show()
            }
        }

        lvList?.setOnItemLongClickListener { parent, _, position, _ ->
            val cur = parent.adapter.getItem(position) as MapperEntity
            when (cur) {
                is CooperateEntity -> {
                    MaterialAlertDialogBuilder(c)
                        .setTitle("删除 ${cur.name}")
                        .setPositiveButton(c.getString(R.string.ok)) { _, _ ->
                            CooperateMap.getInstance(CooperateMap::class.java).remove(cur.id)
                            selectModelFieldFunc?.remove(cur.id)
                            ListAdapter.get(c).exitFind()
                            ListAdapter.get(c).notifyDataSetChanged()
                        }
                        .setNegativeButton(c.getString(R.string.cancel), null)
                        .show()
                }

                !is AreaCode -> {
                    MaterialAlertDialogBuilder(c)
                        .setTitle("选项")
                        .setAdapter(OptionsAdapter.get(c)) { _, which ->
                            var url: String? = null
                            when (which) {
                                0 -> url =
                                    "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2F60000002.h5app.alipay.com%2Fwww%2Fhome.html%3FuserId%3D"

                                1 -> url =
                                    "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2F66666674.h5app.alipay.com%2Fwww%2Findex.htm%3Fuid%3D"

                                2 -> url =
                                    "alipays://platformapi/startapp?appId=20000166&actionType=profile&userId="

                                3 -> MaterialAlertDialogBuilder(c)
                                    .setTitle("删除 ${cur.name}")
                                    .setPositiveButton(c.getString(R.string.ok)) { _, _ ->
                                        selectModelFieldFunc?.remove(cur.id)
                                        ListAdapter.get(c).exitFind()
                                        ListAdapter.get(c).notifyDataSetChanged()
                                    }
                                    .setNegativeButton(c.getString(R.string.cancel), null)
                                    .show()
                            }
                            if (url != null) {
                                val it = Intent(Intent.ACTION_VIEW, (url + cur.id).toUri())
                                c.startActivity(it)
                            }
                        }
                        .setNegativeButton(c.getString(R.string.cancel), null)
                        .show()
                }
            }
            true
        }

        return v
    }
}
