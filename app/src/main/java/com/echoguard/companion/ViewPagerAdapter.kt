package com.echoguard.companion

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/** Adapter for ViewPager2 with 4 tabs. */
class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DashboardFragment()
            1 -> TechnicalLogFragment()
            2 -> SmsAlertsFragment()
            3 -> EvidenceLogFragment()
            else -> DashboardFragment()
        }
    }
}