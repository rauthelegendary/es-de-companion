package com.esde.companion

import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val apps: List<ResolveInfo>,
    private val packageManager: PackageManager,
    private val onAppClick: (ResolveInfo) -> Unit,
    private val onAppLongClick: (ResolveInfo, View) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.appName.text = app.loadLabel(packageManager)
        holder.appIcon.setImageDrawable(app.loadIcon(packageManager))

        // Regular click to launch app
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }

        // Long click to show options menu
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app, holder.itemView)
            true
        }
    }

    override fun getItemCount() = apps.size
}