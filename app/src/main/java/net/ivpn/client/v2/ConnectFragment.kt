package net.ivpn.client.v2

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.tabs.TabLayoutMediator
import net.ivpn.client.IVPNApplication
import net.ivpn.client.R
import net.ivpn.client.common.prefs.ServerType
import net.ivpn.client.databinding.DialogueLocationBinding
import net.ivpn.client.databinding.DialogueServerListLocationBinding
import net.ivpn.client.databinding.DialogueServerLocationBinding
import net.ivpn.client.databinding.FragmentConnectBinding
import net.ivpn.client.rest.data.model.ServerLocation
import net.ivpn.client.ui.connect.ConnectionNavigator
import net.ivpn.client.ui.connect.ConnectionState
import net.ivpn.client.ui.connect.CreateSessionFragment
import net.ivpn.client.ui.dialog.DialogBuilder
import net.ivpn.client.ui.dialog.Dialogs
import net.ivpn.client.ui.protocol.ProtocolViewModel
import net.ivpn.client.v2.map.MapView
import net.ivpn.client.v2.map.dialogue.ServerLocationDialogueAdapter
import net.ivpn.client.v2.map.model.Location
import net.ivpn.client.v2.network.NetworkViewModel
import net.ivpn.client.v2.viewmodel.*
import net.ivpn.client.vpn.ServiceConstants
import org.slf4j.LoggerFactory
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class ConnectFragment : Fragment(), MultiHopViewModel.MultiHopNavigator,
        ConnectionNavigator{

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ConnectFragment::class.java)
    }

    private lateinit var binding: FragmentConnectBinding
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var createSessionFragment: CreateSessionFragment

    @Inject
    lateinit var antiTracker: AntiTrackerViewModel

    @Inject
    lateinit var multihop: MultiHopViewModel

    @Inject
    lateinit var servers: ServersViewModel

    @Inject
    lateinit var account: AccountViewModel

    @Inject
    lateinit var location: LocationViewModel

    @Inject
    lateinit var protocol: ProtocolViewModel

    @Inject
    lateinit var network: NetworkViewModel

    @Inject
    lateinit var connect: ConnectionViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_connect, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        IVPNApplication.getApplication().appComponent.provideActivityComponent().create().inject(this)
        initViews()
    }

    private fun initViews() {
        bottomSheetBehavior = from(binding.slidingPanel.sheetLayout)
        bottomSheetBehavior.state = STATE_COLLAPSED
        bottomSheetBehavior.halfExpandedRatio = 0.000000001f
        bottomSheetBehavior.setExpandedOffset(100)
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == STATE_HIDDEN || newState == STATE_HALF_EXPANDED) {
                    bottomSheetBehavior.state = STATE_EXPANDED
                } else if (newState == STATE_EXPANDED) {
                    checkLocation()
                }
            }
        })

        multihop.navigator = this
        connect.navigator = this

        binding.location = location
        binding.connection = connect
        binding.servers = servers
        binding.slidingPanel.antitracker = antiTracker
        binding.slidingPanel.multihop = multihop
        binding.slidingPanel.servers = servers
        binding.slidingPanel.protocol = protocol
        binding.slidingPanel.network = network
        binding.slidingPanel.connect = connect
        binding.slidingPanel.cards.location = location

        binding.accountButton.setOnClickListener {
            if (account.authenticated.get()) {
                openAccountScreen()
            } else {
                openLoginScreen()
            }
        }
        binding.slidingPanel.networkLayout.setOnClickListener {
            if (!account.authenticated.get()) {
                openLoginScreen()
            } else {
                openNetworkScreen()
            }
        }
        binding.settingsButton.setOnClickListener {
            openSettingsScreen()
        }
        binding.slidingPanel.protocolLayout.setOnClickListener {
            if (!account.authenticated.get()) {
                openLoginScreen()
            } else {
                openProtocolScreen()
            }
        }
        binding.slidingPanel.enterServerLayout.setOnClickListener {
            if (!account.authenticated.get()) {
                openLoginScreen()
            } else {
                openEnterServerSelectionScreen()
            }
        }
        binding.slidingPanel.exitServerLayout.setOnClickListener {
            if (!account.authenticated.get()) {
                openLoginScreen()
            } else {
                openExitServerSelectionScreen()
            }
        }
        binding.slidingPanel.fastestServerLayout.setOnClickListener {
            if (!account.authenticated.get()) {
                openLoginScreen()
            } else {
                openEnterServerSelectionScreen()
            }
        }
        binding.slidingPanel.pauseButton.setOnClickListener {
            if (!account.authenticated.get()) {
                openLoginScreen()
            } else {
                connect.onPauseRequest()
            }
        }
        binding.slidingPanel.resumeButton.setOnClickListener {
            if (!account.authenticated.get()) {
                openLoginScreen()
            } else {
                connect.onConnectRequest()
            }
        }
        binding.centerLocation.setOnClickListener {
            binding.map.centerMap()
        }

        location.addLocationListener(binding.map.locationListener)
        binding.map.mapListener = object: MapView.MapListener {
            override fun onConnectTap(serverLocation: ServerLocation) {
                if (!account.authenticated.get()) {
                    openLoginScreen()
                } else {
                    servers.setServerLocation(serverLocation)
                    connect.connectIfNot()
                }
            }

            override fun onCheckLocationTap() {
                location.checkLocation()
                bottomSheetBehavior.state = STATE_EXPANDED
            }

            override fun openLocationDialogue(location: Location?) {
                val layoutInflater = LayoutInflater.from(activity)
                val binding: DialogueLocationBinding = DataBindingUtil.inflate(
                        layoutInflater,
                        R.layout.dialogue_location, null, false
                )
                binding.location = location

                val infoPopup = PopupWindow(binding.root,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
                infoPopup.isOutsideTouchable = true
                infoPopup.setBackgroundDrawable(ColorDrawable())

                binding.info.setOnClickListener {
                    this@ConnectFragment.location.checkLocation()
                    bottomSheetBehavior.state = STATE_EXPANDED
                    infoPopup.dismiss()
                }

                var topMargin = 0f
                view?.let {
                    topMargin = (it.height - peekHeight) / 2f + resources.getDimension(R.dimen.map_dialog_inner_vertical_margin)
                }
                infoPopup.showAtLocation(view, Gravity.TOP, 0, topMargin.toInt())
            }

            override fun openGatewayDialogue(list: ArrayList<ServerLocation>) {
                if (list.size == 1) {
                    val layoutInflater = LayoutInflater.from(activity)
                    val binding: DialogueServerLocationBinding = DataBindingUtil.inflate(
                            layoutInflater,
                            R.layout.dialogue_server_location, null, false
                    )
                    binding.server = list[0]

                    val infoPopup = PopupWindow(binding.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT)
                    infoPopup.isOutsideTouchable = true
                    infoPopup.setBackgroundDrawable(ColorDrawable())
                    binding.connectButton.setOnClickListener {
                        if (!account.authenticated.get()) {
                            openLoginScreen()
                        } else {
                            servers.setServerLocation(list[0])
                            connect.connectIfNot()
                        }
                        infoPopup.dismiss()
                    }

                    var topMargin = 0f
                    view?.let {
                        topMargin = (it.height - peekHeight) / 2f + resources.getDimension(R.dimen.map_dialog_inner_vertical_margin)
                    }
                    infoPopup.showAtLocation(view, Gravity.TOP, 0, topMargin.toInt())
                } else {
                    val layoutInflater = LayoutInflater.from(activity)
                    val binding: DialogueServerListLocationBinding = DataBindingUtil.inflate(
                            layoutInflater,
                            R.layout.dialogue_server_list_location, null, false
                    )
                    binding.viewPager.adapter = ServerLocationDialogueAdapter(requireContext(), list)
                    TabLayoutMediator(binding.tabs, binding.viewPager) { tab, _ ->
                        tab.text = null
                    }.attach()
                    binding.prevArrow.visibility = if (binding.viewPager.currentItem == 0) View.INVISIBLE else View.VISIBLE
                    binding.nextArrow.visibility = if (binding.viewPager.currentItem == list.size - 1) View.INVISIBLE else View.VISIBLE
                    binding.prevArrow.setOnClickListener {
                        binding.viewPager.currentItem = max(0, binding.viewPager.currentItem - 1)
                    }
                    binding.nextArrow.setOnClickListener {
                        binding.viewPager.currentItem = min(list.size - 1, binding.viewPager.currentItem + 1)
                    }

                    binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            binding.prevArrow.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
                            binding.nextArrow.visibility = if (position == list.size - 1) View.INVISIBLE else View.VISIBLE
                            super.onPageSelected(position)
                        }
                    })

                    val infoPopup = PopupWindow(binding.root,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT)
                    infoPopup.isOutsideTouchable = true
                    infoPopup.setBackgroundDrawable(ColorDrawable())
                    binding.connectButton.setOnClickListener {
                        if (!account.authenticated.get()) {
                            openLoginScreen()
                        } else {
                            servers.setServerLocation(list[binding.viewPager.currentItem])
                            connect.connectIfNot()
                        }
                        infoPopup.dismiss()
                    }

                    var topMargin = 0f
                    view?.let {
                        topMargin = (it.height - peekHeight) / 2f + resources.getDimension(R.dimen.map_dialog_inner_vertical_margin)
                    }
                    infoPopup.showAtLocation(view, Gravity.TOP, 0, topMargin.toInt())
                }
            }
        }
    }

    override fun onResume() {
        println("Connect Fragment onResume")
        super.onResume()
        servers.onResume()
        account.onResume()
        multihop.onResume()
        applySlidingPanelSide()
        checkLocationPermission()
    }

    override fun onStart() {
        println("Connect Fragment onStart")
        super.onStart()
        network.onStart()
    }

    override fun onStop() {
        println("Connect Fragment onStop")
        super.onStop()
        network.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        location.removeLocationListener(binding.map.locationListener)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            LOGGER.info("onActivityResult: RESULT_CANCELED")
            return
        }

        LOGGER.info("onActivityResult: RESULT_OK")
        when (requestCode) {
            ServiceConstants.IVPN_REQUEST_CODE -> {
                connect.onConnectRequest()
            }
            ServiceConstants.KILL_SWITCH_REQUEST_CODE -> {
            }
        }
    }

    private fun applySlidingPanelSide() {
        recalculatePeekHeight()
    }

    private fun checkLocation() {
    }

    private fun checkLocationPermission() {
    }

    private fun checkVPNPermission(requestCode: Int) {
        LOGGER.info("checkVPNPermission")
        val intent: Intent?
        intent = try {
            VpnService.prepare(context)
        } catch (exception: Exception) {
            exception.printStackTrace()
            openErrorDialog(Dialogs.FIRMWARE_ERROR)
            return
        }
        if (intent != null) {
            try {
                startActivityForResult(intent, requestCode)
            } catch (ane: ActivityNotFoundException) {
                LOGGER.error("Error while checking VPN permission", ane)
            }
        } else {
            onActivityResult(requestCode, Activity.RESULT_OK, null)
        }
    }

    override fun onMultiHopStateChanged(state: Boolean) {
        recalculatePeekHeight()
    }

    override fun subscribe() {

    }

    override fun authenticate() {
    }

    override fun notifyUser(msgId: Int, actionId: Int) {
    }

    var peekHeight: Float = 0f
    private fun recalculatePeekHeight() {
        if (context == null) {
            return
        }
        peekHeight = resources.getDimension(R.dimen.slider_layout_basic_height)
        if (multihop.isEnabled.get()) {
            peekHeight += resources.getDimension(R.dimen.slider_layout_exit_layout_height)
            binding.slidingPanel.exitServerLayout.visibility = View.VISIBLE
        } else {
            Handler().postDelayed({
                binding.slidingPanel.exitServerLayout.visibility = View.GONE
            }, 50)
        }
        if (multihop.isSupported.get()) {
            peekHeight += resources.getDimension(R.dimen.slider_layout_multihop_switch_height)
        }
        bottomSheetBehavior.setPeekHeight(peekHeight.toInt(), true)
        binding.map.setPanelHeight(peekHeight)
        binding.centerLocation.animate().translationY(-peekHeight)
    }

    private fun openSettingsScreen() {
        val action = ConnectFragmentDirections.actionConnectFragmentToSettingsFragment()
        NavHostFragment.findNavController(this).navigate(action)
    }

    private fun openNetworkScreen() {
        val action = ConnectFragmentDirections.actionConnectFragmentToNetworkProtectionFragment()
        NavHostFragment.findNavController(this).navigate(action)
    }

    private fun openLoginScreen() {
        val action = ConnectFragmentDirections.actionConnectFragmentToLoginFragment()
        NavHostFragment.findNavController(this).navigate(action)
    }

    private fun openAccountScreen() {
        val action = ConnectFragmentDirections.actionConnectFragmentToAccountFragment()
        NavHostFragment.findNavController(this).navigate(action)
    }

    private fun openProtocolScreen() {
        val action = ConnectFragmentDirections.actionConnectFragmentToProtocolFragment()
        NavHostFragment.findNavController(this).navigate(action)
    }

    private fun openEnterServerSelectionScreen() {
        val action = ConnectFragmentDirections.actionConnectFragmentToServerListFragment(ServerType.ENTRY)
        NavHostFragment.findNavController(this).navigate(action)
    }

    private fun openExitServerSelectionScreen() {
        val action = ConnectFragmentDirections.actionConnectFragmentToServerListFragment(ServerType.EXIT)
        NavHostFragment.findNavController(this).navigate(action)
    }

    private fun disconnectVpnService(needToReset: Boolean, dialog: Dialogs?,
                                     listener: DialogInterface.OnClickListener) {
        if (dialog != null) {
            DialogBuilder.createOptionDialog(context, dialog, listener)
        }
    }

    override fun openNoNetworkDialog() {
        openErrorDialog(Dialogs.CONNECTION_ERROR)
    }

    override fun openErrorDialog(dialogs: Dialogs) {
        DialogBuilder.createNotificationDialog(context, dialogs)
    }

    override fun onAuthFailed() {
        LOGGER.info("onAuthFailed")
        disconnectVpnService(true, Dialogs.ON_CONNECTION_AUTHENTICATION_ERROR,
                DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                    LOGGER.info("onClick: ")
                    logout()
                })
    }

    override fun onChangeConnectionStatus(state: ConnectionState) {
    }

    override fun askConnectionPermission() {
        if (!account.authenticated.get()) {
            openLoginScreen()
        } else {
            checkVPNPermission(ServiceConstants.IVPN_REQUEST_CODE)
        }
    }

    override fun notifyAnotherPortUsedToConnect() {
    }

    override fun accountVerificationFailed() {
        LOGGER.info("accountVerificationFailed")
        DialogBuilder.createNonCancelableDialog(context, Dialogs.SESSION_HAS_EXPIRED,
                { _: DialogInterface?, _: Int ->
                    LOGGER.info("onClick: ")
                    logout()
                },
                {
                    LOGGER.info("onCancel: ")
                    logout()
                })
    }

    override fun openSessionLimitReachedDialogue() {
        createSessionFragment = CreateSessionFragment()
        createSessionFragment.show(childFragmentManager, createSessionFragment.tag)
    }

    override fun onTimeOut() {
        LOGGER.info("onTimeOut")
        disconnectVpnService(true, Dialogs.TRY_RECONNECT,
                DialogInterface.OnClickListener { _: DialogInterface?, _: Int -> connect.onConnectRequest() })
    }

    override fun logout() {
    }
}