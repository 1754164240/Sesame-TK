package fansirsqi.xposed.sesame.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.entity.MapperEntity
import fansirsqi.xposed.sesame.model.SelectModelFieldFunc
import fansirsqi.xposed.sesame.util.Log

class ListAdapter(private val context: Context) : BaseAdapter() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var adapter: ListAdapter? = null
        var listType: ListDialog.ListType? = null
        val viewHolderList = mutableListOf<ViewHolder>()

        fun get(c: Context): ListAdapter {
            if (adapter == null) {
                adapter = ListAdapter(c.applicationContext)
            }
            return adapter!!
        }

        fun getClear(c: Context): ListAdapter {
            val adapter = get(c)
            adapter.resetFindState()
            return adapter
        }

        fun getClear(c: Context, type: ListDialog.ListType): ListAdapter {
            val adapter = get(c)
            listType = type
            adapter.resetFindState()
            return adapter
        }
    }

    private var list: List<MapperEntity> = emptyList()
    private var selectModelFieldFunc: SelectModelFieldFunc? = null
    private var findIndex = -1
    private var findWord: String? = null

    fun setBaseList(l: List<MapperEntity>) {
        if (l != list) exitFind()
        list = l
    }

    fun setSelectedList(func: SelectModelFieldFunc) {
        selectModelFieldFunc = func
        try {
            list = list.sortedWith { o1, o2 ->
                val contains1 = func.contains(o1.id) == true
                val contains2 = func.contains(o2.id) == true
                if (contains1 == contains2) o1.compareTo(o2) else if (contains1) -1 else 1
            }
        } catch (e: Exception) {
            Log.printStackTrace("ListAdapter error", e)
        }
    }

    fun findLast(findThis: String): Int = findItem(findThis, false)
    fun findNext(findThis: String): Int = findItem(findThis, true)

    private fun findItem(findThisInput: String, forward: Boolean): Int {
        if (list.isEmpty()) return -1
        val findThis = findThisInput.lowercase()
        if (findThis != findWord) {
            resetFindState()
            findWord = findThis
        }
        var current = findIndex.coerceAtLeast(0)
        val size = list.size
        val start = current
        do {
            current = if (forward) (current + 1) % size else (current - 1 + size) % size
            if (list[current].name.lowercase().contains(findThis)) {
                findIndex = current
                notifyDataSetChanged()
                return findIndex
            }
        } while (current != start)
        return -1
    }

    fun resetFindState() {
        findIndex = -1
        findWord = null
    }

    fun exitFind() = resetFindState()

    fun selectAll() {
        selectModelFieldFunc?.clear()
        list.forEach { selectModelFieldFunc?.add(it.id, 0) }
        notifyDataSetChanged()
    }

    fun selectInvert() {
        list.forEach {
            if (selectModelFieldFunc?.contains(it.id) == false) selectModelFieldFunc?.add(it.id, 0)
            else selectModelFieldFunc?.remove(it.id)
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = list.size
    override fun getItem(position: Int): Any = list[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var vh: ViewHolder
        val view: View = convertView ?: run {
            vh = ViewHolder()
            val newView = View.inflate(context, R.layout.list_item, null)
            vh.tv = newView.findViewById(R.id.tv_idn)
            vh.cb = newView.findViewById(R.id.cb_list)
            if (listType == ListDialog.ListType.SHOW) vh.cb?.visibility = View.GONE
            newView.tag = vh
            viewHolderList.add(vh)
            newView
        }
        vh = view.tag as ViewHolder
        val item = list[position]
        vh.tv.text = item.name
        val textColorPrimary = ContextCompat.getColor(context, R.color.textColorPrimary)
        vh.tv.setTextColor(if (findIndex == position) Color.RED else textColorPrimary)
        vh.cb.isChecked = selectModelFieldFunc?.contains(item.id) == true
        return view
    }

    class ViewHolder {
        lateinit var tv: TextView
        lateinit var cb: CheckBox
    }

}
