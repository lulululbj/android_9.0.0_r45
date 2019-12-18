## 前言

这是 [Android 9.0 AOSP 系列](https://github.com/lulululbj/android_9.0.0_r45) 的第五篇了，先来回顾一下前面几篇的大致内容。

> [Java 世界的盘古和女娲 —— Zygote](https://juejin.im/post/5d8f73bf51882555b149dc64)

主要介绍了 Android 世界的第一个 Java 进程 `Zygote` 的启动过程。

* 注册服务端 socket，用于响应客户端请求
* 各种预加载操作，类，资源，共享库等
* 强制 GC 一次
* fork SystemServer 进程
* 循环等待客户端发来的 socket 请求（请求 socket 连接和请求 fork 应用进程）


> [Zygote家的大儿子 —— SystemServer](https://juejin.im/post/5da341f451882561ba64b9da)

主要介绍了 Zygote 进程 fork 的第一个进程 `SystemServer`，它承载了各类系统服务的创建和启动。

* 语言、时区、地区等设置

* 虚拟机内存设置

* 指纹信息，Binder 调用设置

* `Looper.prepareMainLooper()` ，创建主线程 Looper

* 初始化 native 服务，加载 `libandroid_servers.so`

* `createSystemContext()`，初始化系统上下文

* 创建系统服务管理者 `SystemServiceManager`

* `startBootstrapServices`，启动系统引导服务

* `startCoreServices`，启动系统核心服务

* `startOtherServices`，启动其他服务

* `Looper.loop()`，开启消息循环

在 `startOtherServices` 的最后会调用 AMS 的 `onSystemReady()` 方法启动桌面 Activity。


> [Android 世界中，谁喊醒了 Zygote ？](https://juejin.im/post/5da5e7da518825740064f951)

主要介绍了 AMS 向 Zygote 请求创建应用进程的过程，即向 Zygote 进程进行 socket 通信，与第一篇呼应。

* 调用 `Process.start()` 创建应用进程

* `ZygoteProcess` 负责和 `Zygote` 进程建立 socket 连接，并将创建进程需要的参数发送给 Zygote 的 socket 服务端

* `Zygote` 服务端接收到参数之后调用 `ZygoteConnection.processOneCommand()` 处理参数，并 fork 进程

* 最后通过 `findStaticMain()` 找到 `ActivityThread` 类的 main() 方法并执行，子进程就启动了

> [“无处不在” 的系统核心服务 —— ActivityManagerService 启动流程解析](https://juejin.im/post/5db5b61f51882556a035af3e)

主要介绍了 `ActivityManagerService (AMS)` 的启动流程，它与四大组件的启动，切换，调度以及应用进程的管理息息相关。

* AMS 初始化，通过 `ActivityManagerService.Lifecycle` 的构造函数中初始化

* `setSystemProcess()`，注册各种服务，创建 ProcessRecord，更新 oom_adj 值

* 安装系统 Provider

* `systemReady()`，最终会启动桌面 Home Activity

今天要介绍的就是 Activity 的启动流程了。Activity 的启动是个大工程，细节十分之多。这篇文章会简单梳理整个启动流程，不会过度深入源码细节。对其中的关键问题，如 launchMode 的处理，生命周期的处理，后续会通过单独的文章深入剖析。


## 启动流程分析

先来一张流程图，对照着看更方便理解。


![](https://user-gold-cdn.xitu.io/2019/12/3/16ecc331ed9cb532?w=1813&h=1221&f=png&s=136689)

接着之前的分析，ActivityManagerService 的 `systemReady()` 方法中最后会去启动桌面 Hme Activity，调用的方法是 `startHomeActivityLocked` 。

```java
> ActivityManagerService.java

boolean startHomeActivityLocked(int userId, String reason) {
    ......
    Intent intent = getHomeIntent();
    ActivityInfo aInfo = resolveActivityInfo(intent, STOCK_PM_FLAGS, userId);
    if (aInfo != null) {
        ......
        if (app == null || app.instr == null) {
            intent.setFlags(intent.getFlags() | FLAG_ACTIVITY_NEW_TASK);
            final int resolvedUserId = UserHandle.getUserId(aInfo.applicationInfo.uid);
            final String myReason = reason + ":" + userId + ":" + resolvedUserId;
            // 启动桌面 Activity
            mActivityStartController.startHomeActivity(intent, aInfo, myReason);
        }
    } else {
        Slog.wtf(TAG, "No home screen found for " + intent, new Throwable());
    }
    return true;
}
```

调用 `ActivityStartController`  的 `startHomeActivity()` 方法：

```java
> ActivityStartController.java

void startHomeActivity(Intent intent, ActivityInfo aInfo, String reason) {
        mSupervisor.moveHomeStackTaskToTop(reason);

        mLastHomeActivityStartResult = obtainStarter(intent, "startHomeActivity: " + reason)
                .setOutActivity(tmpOutRecord)
                .setCallingUid(0)
                .setActivityInfo(aInfo)
                .execute();
        mLastHomeActivityStartRecord = tmpOutRecord[0];
        if (mSupervisor.inResumeTopActivity) {
            mSupervisor.scheduleResumeTopActivities();
        }
    }
```

`obtainStarter()` 方法返回的是 `ActivityStarter` 对象，它负责 Activity 的启动，一系列 `setXXX()` 方法传入启动所需的各种参数，最后的 `execute()` 是真正的启动逻辑。

在继续看源码之前，先思考一下现在处于哪个进程？AMS 是在 `system_server` 进程中初始化的，所以上面的工作都是在 `system_server` 进程发生的。而我们通常在开发过程中使用的 `startActivity()` 方法显然是在应用进程调用的。那么，普通的 `startActivity()` 方法又是怎么样的调用链呢？跟进 `Activity.startActivity()` 方法来看一下。

```java
> Activity.java    

@Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        if (options != null) {
            startActivityForResult(intent, -1, options);
        } else {
            startActivityForResult(intent, -1);
        }
    }

    public void startActivityForResult(@RequiresPermission Intent intent, int requestCode,
            @Nullable Bundle options) {
        if (mParent == null) {
            options = transferSpringboardActivityOptions(options);
            // 调用 Instrumentation.execStartActivity() 方法
            Instrumentation.ActivityResult ar =
                mInstrumentation.execStartActivity(
                    this, mMainThread.getApplicationThread(), mToken, this,
                    intent, requestCode, options);
            if (ar != null) {
              // 回调 ActivityResult
                mMainThread.sendActivityResult(
                    mToken, mEmbeddedID, requestCode, ar.getResultCode(),
                    ar.getResultData());
            }
            if (requestCode >= 0) {
                mStartedActivity = true;
            }

            cancelInputsAndStartExitTransition(options);
        } else {
          // 最终也是调用 Instrumentation.execStartActivity() 方法
            if (options != null) {
                mParent.startActivityFromChild(this, intent, requestCode, options);
            } else {
                mParent.startActivityFromChild(this, intent, requestCode);
            }
        }
    }
```



最终都会调用 `Instrumentation`  的 `execStartActivity()` 方法。Instrumentation 是个非常重要的类，Activity 的启动，生命周期的回调都离不开它。后面会多次遇到这个类。


```java
> Instrumentation.java

public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        IApplicationThread whoThread = (IApplicationThread) contextThread;
 		    ......
        try {
            intent.migrateExtraStreamToClipData();
            intent.prepareToLeaveProcess(who);
            // Binder 调用 AMS 来启动 Activity
            int result = ActivityManager.getService()
                .startActivity(whoThread, who.getBasePackageName(), intent,
                        intent.resolveTypeIfNeeded(who.getContentResolver()),
                        token, target != null ? target.mEmbeddedID : null,
                        requestCode, 0, null, options);
            // 检测启动结果
            checkStartActivityResult(result, intent);
        } catch (RemoteException e) {
            throw new RuntimeException("Failure from system", e);
        }
        return null;
    }
```

这里通过 Binder 调用 AMS 的 `startActivity()` 方法。`ActivityManager.getService()` 不用多想肯定是获取 AMS 代理对象的。

```java
> ActivityManager.java

public static IActivityManager getService() {
    return IActivityManagerSingleton.get();
}

private static final Singleton<IActivityManager> IActivityManagerSingleton =
        new Singleton<IActivityManager>() {
            @Override
            protected IActivityManager create() {
                final IBinder b = ServiceManager.getService(Context.ACTIVITY_SERVICE);
                final IActivityManager am = IActivityManager.Stub.asInterface(b);
                return am;
            }
        };
```

接着就进入到  AMS 的 startActivity() 方法。

```java
> ActivityManagerService.java    

@Override
    public final int startActivity(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, ProfilerInfo profilerInfo, Bundle bOptions) {setMayWait
        return startActivityAsUser(caller, callingPackage, intent, resolvedType, resultTo,
                resultWho, requestCode, startFlags, profilerInfo, bOptions,
                UserHandle.getCallingUserId());
    }

    public final int startActivityAsUser(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId,
            boolean validateIncomingUser) {
        enforceNotIsolatedCaller("startActivity");

        userId = mActivityStartController.checkTargetUser(userId, validateIncomingUser,
                Binder.getCallingPid(), Binder.getCallingUid(), "startActivityAsUser");

        // TODO: Switch to user app stacks here.
        return mActivityStartController.obtainStarter(intent, "startActivityAsUser") // 获取 ActivityStarter 对象
                .setCaller(caller)
                .setCallingPackage(callingPackage)
                .setResolvedType(resolvedType)
                .setResultTo(resultTo)
                .setResultWho(resultWho)
                .setRequestCode(requestCode)
                .setStartFlags(startFlags)
                .setProfilerInfo(profilerInfo)
                .setActivityOptions(bOptions)
                .setMayWait(userId)
                .execute();

    }
```

接下来和之前启动 Home Activity 比较相似了。获取 `ActivityStarter` 对象，提供参数，最后 `execute()` 。

`obtainStarter()` 通过工厂模式获取 ActivityStarter 对象。

```java
    ActivityStarter obtainStarter(Intent intent, String reason) {
        return mFactory.obtain().setIntent(intent).setReason(reason);
    }
```

mFactory 的默认实现是 `ActivityStarter.DefaultFactory` 。

```java
> ActivityStarter.java   

static class DefaultFactory implements Factory {
        /**
         * The maximum count of starters that should be active at one time:
         * 1. last ran starter (for logging and post activity processing)
         * 2. current running starter
         * 3. starter from re-entry in (2)
         *
         * 同时激活的 starter 最多只能有三个。
         */
        private final int MAX_STARTER_COUNT = 3;

        private ActivityStartController mController;
        private ActivityManagerService mService;
        private ActivityStackSupervisor mSupervisor;
        private ActivityStartInterceptor mInterceptor;

        private SynchronizedPool<ActivityStarter> mStarterPool =
                new SynchronizedPool<>(MAX_STARTER_COUNT);

        DefaultFactory(ActivityManagerService service,
                ActivityStackSupervisor supervisor, ActivityStartInterceptor interceptor) {
            mService = service;
            mSupervisor = supervisor;
            mInterceptor = interceptor;
        }

        @Override
        public void setController(ActivityStartController controller) {
            mController = controller;
        }

        @Override
        public ActivityStarter obtain() {
            // 从同步对象池 SynchronizedPool 中获取
            ActivityStarter starter = mStarterPool.acquire();

            if (starter == null) {
                starter = new ActivityStarter(mController, mService, mSupervisor, mInterceptor);
            }

            return starter;
        }

        @Override
        public void recycle(ActivityStarter starter) {
            starter.reset(true /* clearRequest*/);
            mStarterPool.release(starter);
        }
    }
```

提供了一个容量为 3 的同步对象缓存池来缓存 ActivityStarter 对象。`setXXX()` 方法均为参数配置，注意 `setMayWait` 方法会将 `mayWait` 参数置为 true。我们直接看它的实际执行过程，`execute()` 函数。

```java
> ActivityStarter.java       

int execute() {
        try {
            if (mRequest.mayWait) { // setMayWait() 方法中将 mayWait 置为 true
                return startActivityMayWait(mRequest.caller, mRequest.callingUid,
                        mRequest.callingPackage, mRequest.intent, mRequest.resolvedType,
                        mRequest.voiceSession, mRequest.voiceInteractor, mRequest.resultTo,
                        mRequest.resultWho, mRequest.requestCode, mRequest.startFlags,
                        mRequest.profilerInfo, mRequest.waitResult, mRequest.globalConfig,
                        mRequest.activityOptions, mRequest.ignoreTargetSecurity, mRequest.userId,
                        mRequest.inTask, mRequest.reason,
                        mRequest.allowPendingRemoteAnimationRegistryLookup,
                        mRequest.originatingPendingIntent);
            } else {
                return startActivity(mRequest.caller, mRequest.intent, mRequest.ephemeralIntent,
                        mRequest.resolvedType, mRequest.activityInfo, mRequest.resolveInfo,
                        mRequest.voiceSession, mRequest.voiceInteractor, mRequest.resultTo,
                        mRequest.resultWho, mRequest.requestCode, mRequest.callingPid,
                        mRequest.callingUid, mRequest.callingPackage, mRequest.realCallingPid,
                        mRequest.realCallingUid, mRequest.startFlags, mRequest.activityOptions,
                        mRequest.ignoreTargetSecurity, mRequest.componentSpecified,
                        mRequest.outActivity, mRequest.inTask, mRequest.reason,
                        mRequest.allowPendingRemoteAnimationRegistryLookup,
                        mRequest.originatingPendingIntent);
            }
        } finally {
            // 回收当前 ActivityStarter 对象
            onExecutionComplete();
        }
    }
```

接着调用 `startActivityMayWait()` 。

```java
>  ActivityStarter.java

private int startActivityMayWait(IApplicationThread caller, int callingUid,
            String callingPackage, Intent intent, String resolvedType,
            IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
            IBinder resultTo, String resultWho, int requestCode, int startFlags,
            ProfilerInfo profilerInfo, WaitResult outResult,
            Configuration globalConfig, SafeActivityOptions options, boolean ignoreTargetSecurity,
            int userId, TaskRecord inTask, String reason,
            boolean allowPendingRemoteAnimationRegistryLookup,
            PendingIntentRecord originatingPendingIntent) {
       .....

        // Save a copy in case ephemeral needs it
        final Intent ephemeralIntent = new Intent(intent);
        // Don't modify the client's object!
        // 重新创建，不修改客户端原来的 intent
        intent = new Intent(intent);
        if (componentSpecified
                && !(Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() == null)
                && !Intent.ACTION_INSTALL_INSTANT_APP_PACKAGE.equals(intent.getAction())
                && !Intent.ACTION_RESOLVE_INSTANT_APP_PACKAGE.equals(intent.getAction())
                && mService.getPackageManagerInternalLocked()
                        .isInstantAppInstallerComponent(intent.getComponent())) {
            intent.setComponent(null /*component*/);
            componentSpecified = false;
        }

        // 获取 ResolveInfo
        ResolveInfo rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId,
                0 /* matchFlags */,
                        computeResolveFilterUid(
                                callingUid, realCallingUid, mRequest.filterCallingUid));
        ......
        // Collect information about the target of the Intent.
        // 获取目标 Intent 的 ActivityInfo
        ActivityInfo aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, profilerInfo);

        synchronized (mService) {
            final ActivityStack stack = mSupervisor.mFocusedStack;
            stack.mConfigWillChange = globalConfig != null
                    && mService.getGlobalConfiguration().diff(globalConfig) != 0;
            ......

            final ActivityRecord[] outRecord = new ActivityRecord[1];
            // 调用 startActivity() 方法
            int res = startActivity(caller, intent, ephemeralIntent, resolvedType, aInfo, rInfo,
                    voiceSession, voiceInteractor, resultTo, resultWho, requestCode, callingPid,
                    callingUid, callingPackage, realCallingPid, realCallingUid, startFlags, options,
                    ignoreTargetSecurity, componentSpecified, outRecord, inTask, reason,
                    allowPendingRemoteAnimationRegistryLookup, originatingPendingIntent);

            Binder.restoreCallingIdentity(origId);

            ......

            if (outResult != null) {
                // 设置启动结果
                outResult.result = res;

                final ActivityRecord r = outRecord[0];

                switch(res) {
                    case START_SUCCESS: {
                        mSupervisor.mWaitingActivityLaunched.add(outResult);
                        do {
                            try {
                                // 等待启动结果
                                mService.wait();
                            } catch (InterruptedException e) {
                            }
                        } while (outResult.result != START_TASK_TO_FRONT
                                && !outResult.timeout && outResult.who == null);
                        if (outResult.result == START_TASK_TO_FRONT) {
                            res = START_TASK_TO_FRONT;
                        }
                        break;
                    }
                     ......
                        break;
                    }
                }
            }

            return res;
        }
    }
```

调动 `startActivity()` 方法来启动 Activity，它有两个重载方法被依次调用。这里会等待启动结果。


```java
>  ActivityStarter.java  

private int startActivity(IApplicationThread caller, Intent intent, Intent ephemeralIntent,
            String resolvedType, ActivityInfo aInfo, ResolveInfo rInfo,
            IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
            IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid,
            String callingPackage, int realCallingPid, int realCallingUid, int startFlags,
            SafeActivityOptions options,
            boolean ignoreTargetSecurity, boolean componentSpecified, ActivityRecord[] outActivity,
            TaskRecord inTask, boolean allowPendingRemoteAnimationRegistryLookup,
            PendingIntentRecord originatingPendingIntent) {
        int err = ActivityManager.START_SUCCESS;

        ProcessRecord callerApp = null;
        if (caller != null) {
            // caller 不为空时，通过 AMS 查找 ProcessRecord
            callerApp = mService.getRecordForAppLocked(caller);
            if (callerApp != null) {
                callingPid = callerApp.pid;
                callingUid = callerApp.info.uid;
            } else {
                err = ActivityManager.START_PERMISSION_DENIED;
            }
        }

        // sourceRecord 用于描述发起本次请求的 Activity
        // resultRecord 用户描述接收启动结果的 Activity
        // 一般情况下，这两个 Activity 应该是同一个
        ActivityRecord sourceRecord = null;
        ActivityRecord resultRecord = null;
        ......
        // 获取启动标志
        final int launchFlags = intent.getFlags();

        ......

        if (err == ActivityManager.START_SUCCESS && intent.getComponent() == null) {
            // 未找到可以处理该 intent 的类
            err = ActivityManager.START_INTENT_NOT_RESOLVED;
        }

        if (err == ActivityManager.START_SUCCESS && aInfo == null) {
            // 没有找到 intent 中指定的 Activity 类
            err = ActivityManager.START_CLASS_NOT_FOUND;
        }

        ......

        // 权限检查
        boolean abort = !mSupervisor.checkStartAnyActivityPermission(intent, aInfo, resultWho,
                requestCode, callingPid, callingUid, callingPackage, ignoreTargetSecurity,
                inTask != null, callerApp, resultRecord, resultStack);
        abort |= !mService.mIntentFirewall.checkStartActivity(intent, callingUid,
                callingPid, resolvedType, aInfo.applicationInfo);

        ......

        // 构建 ActivityRecord
        ActivityRecord r = new ActivityRecord(mService, callerApp, callingPid, callingUid,
                callingPackage, intent, resolvedType, aInfo, mService.getGlobalConfiguration(),
                resultRecord, resultWho, requestCode, componentSpecified, voiceSession != null,
                mSupervisor, checkedOptions, sourceRecord);

        ......

        // 获取当前获取焦点的 ActivityStack
        final ActivityStack stack = mSupervisor.mFocusedStack;

        // 如果启动一个和当前处于 resume 状态的 activity 不同 uid 的新 activity，要检查是否允许 app 切换
        if (voiceSession == null && (stack.getResumedActivity() == null
                || stack.getResumedActivity().info.applicationInfo.uid != realCallingUid)) {
            if (!mService.checkAppSwitchAllowedLocked(callingPid, callingUid,
                    realCallingPid, realCallingUid, "Activity start")) {
                mController.addPendingActivityLaunch(new PendingActivityLaunch(r,
                        sourceRecord, startFlags, stack, callerApp));
                ActivityOptions.abort(checkedOptions);
                // 不允许切换，直接返回
                return ActivityManager.START_SWITCHES_CANCELED;
            }
        }

        ......

        // 调用重载方法
        return startActivity(r, sourceRecord, voiceSession, voiceInteractor, startFlags,
                true /* doResume */, checkedOptions, inTask, outActivity);
    }
```



```java
>  ActivityStarter.java

        private int startActivity(final ActivityRecord r, ActivityRecord sourceRecord,
                IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
                int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,
                ActivityRecord[] outActivity) {
        int result = START_CANCELED;
        try {
            // 延时布局
            mService.mWindowManager.deferSurfaceLayout();
            // 调用 startActivityUnchecked() 方法
            result = startActivityUnchecked(r, sourceRecord, voiceSession, voiceInteractor,
                    startFlags, doResume, options, inTask, outActivity);
        } finally {
            final ActivityStack stack = mStartActivity.getStack();
            if (!ActivityManager.isStartResultSuccessful(result) && stack != null) {
                stack.finishActivityLocked(mStartActivity, RESULT_CANCELED,
                        null /* intentResultData */, "startActivity", true /* oomAdj */);
            }
            // 恢复布局
            mService.mWindowManager.continueSurfaceLayout();
        }

        postStartActivityProcessing(r, result, mTargetStack);

        return result;
    }
```

接着调用 `startActivityUnchecked()` 。


```java
>  ActivityStarter.java

    private int startActivityUnchecked(final ActivityRecord r, ActivityRecord sourceRecord,
            IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
            int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,
            ActivityRecord[] outActivity) {

        // 设置启动 Activity 的初始状态，包括 flag
        setInitialState(r, options, inTask, doResume, startFlags, sourceRecord, voiceSession,
                voiceInteractor);

        // 计算 mLaunchFlags ，启动标志位
        computeLaunchingTaskFlags();

        // 计算 mSourceStack
        computeSourceStack();

        // 设置启动标志位
        mIntent.setFlags(mLaunchFlags);

        // 查找可复用的 Activity
        ActivityRecord reusedActivity = getReusableIntentActivity();

       ......

        // 不等于 null 说明新的 activity 应该插入已存在的任务栈中
        if (reusedActivity != null) {
            if (mService.getLockTaskController().isLockTaskModeViolation(reusedActivity.getTask(),
                    (mLaunchFlags & (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK))
                            == (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK))) {
                Slog.e(TAG, "startActivityUnchecked: Attempt to violate Lock Task Mode");
                return START_RETURN_LOCK_TASK_MODE_VIOLATION;
            }

            final boolean clearTopAndResetStandardLaunchMode =
                    (mLaunchFlags & (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                            == (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    && mLaunchMode == LAUNCH_MULTIPLE;

            if (mStartActivity.getTask() == null && !clearTopAndResetStandardLaunchMode) {
                mStartActivity.setTask(reusedActivity.getTask());
            }

            if (reusedActivity.getTask().intent == null) {
                reusedActivity.getTask().setIntent(mStartActivity);
            }

            if ((mLaunchFlags & FLAG_ACTIVITY_CLEAR_TOP) != 0
                    || isDocumentLaunchesIntoExisting(mLaunchFlags)
                    || isLaunchModeOneOf(LAUNCH_SINGLE_INSTANCE, LAUNCH_SINGLE_TASK)) {
                final TaskRecord task = reusedActivity.getTask();

                // 清空任务栈
                final ActivityRecord top = task.performClearTaskForReuseLocked(mStartActivity,
                        mLaunchFlags);

                if (reusedActivity.getTask() == null) {
                    reusedActivity.setTask(task);
                }

                if (top != null) {
                    if (top.frontOfTask) {
                        top.getTask().setIntent(mStartActivity);
                    }
                    // 触发 onNewIntent()
                    deliverNewIntent(top);
                }
            }

        ......

        // 是否创建新的 task
        boolean newTask = false;
        final TaskRecord taskToAffiliate = (mLaunchTaskBehind && mSourceRecord != null)
                ? mSourceRecord.getTask() : null;

        ......

        // 将要启动的 Activity 在 Task 中置顶
        mTargetStack.startActivityLocked(mStartActivity, topFocused, newTask, mKeepCurTransition,
                mOptions);
        if (mDoResume) {
            final ActivityRecord topTaskActivity =
                    mStartActivity.getTask().topRunningActivityLocked();
            if (!mTargetStack.isFocusable()
                    || (topTaskActivity != null && topTaskActivity.mTaskOverlay
                    && mStartActivity != topTaskActivity)) {
                mTargetStack.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
                mService.mWindowManager.executeAppTransition();
            } else {
                if (mTargetStack.isFocusable() && !mSupervisor.isFocusedStack(mTargetStack)) {
                    mTargetStack.moveToFront("startActivityUnchecked");
                }
                // 调用 ActivityStackSupervisor.resumeFocusedStackTopActivityLocked() 方法
                mSupervisor.resumeFocusedStackTopActivityLocked(mTargetStack, mStartActivity,
                        mOptions);
            }
        } else if (mStartActivity != null) {
            mSupervisor.mRecentTasks.add(mStartActivity.getTask());
        }
        mSupervisor.updateUserStackLocked(mStartActivity.userId, mTargetStack);

        mSupervisor.handleNonResizableTaskIfNeeded(mStartActivity.getTask(), preferredWindowingMode,
                preferredLaunchDisplayId, mTargetStack);

        return START_SUCCESS;
    }
```

`startActivityUnchecked()` 方法主要除了处理了启动标记 flag ，要启动的任务栈等。这一块源码很长，上面作了大量删减，仅保留了基本的调用链。感兴趣的同学可以自行查看源文件。接下来 调用了  `ActivityStackSupervisor` 的  `resumeFocusedStackTopActivityLocked()` 方法。



```java
> ActivityStackSupervisor.java

        boolean resumeFocusedStackTopActivityLocked(
            ActivityStack targetStack, ActivityRecord target, ActivityOptions targetOptions) {

        if (!readyToResume()) {
            return false;
        }

        // 目标 Stack 就是 mFocusedStack
        if (targetStack != null && isFocusedStack(targetStack)) {
            return targetStack.resumeTopActivityUncheckedLocked(target, targetOptions);
        }

        // 获取 mFocusedStack 栈顶的 ActivityRecord
        final ActivityRecord r = mFocusedStack.topRunningActivityLocked();
        if (r == null || !r.isState(RESUMED)) {
            mFocusedStack.resumeTopActivityUncheckedLocked(null, null);
        } else if (r.isState(RESUMED)) {
            // Kick off any lingering app transitions form the MoveTaskToFront operation.
            mFocusedStack.executeAppTransition(targetOptions);
        }

        return false;
    }
```

获取待启动 Activity 的 ActivityStack 之后并调用其 `resumeTopActivityUncheckedLocked()` 方法。

```java
> ActivityStack.java

boolean resumeTopActivityUncheckedLocked(ActivityRecord prev, ActivityOptions options) {
        if (mStackSupervisor.inResumeTopActivity) {
            // 防止递归启动
            return false;
        }

        boolean result = false;
        try {
            mStackSupervisor.inResumeTopActivity = true;
            // 执行 resumeTopActivityInnerLocked(） 方法)
            result = resumeTopActivityInnerLocked(prev, options);

            final ActivityRecord next = topRunningActivityLocked(true /* focusableOnly */);
            if (next == null || !next.canTurnScreenOn()) {
                checkReadyForSleep();
            }
        } finally {
            mStackSupervisor.inResumeTopActivity = false;
        }

        return result;
    }

```


```JAVA
> ActivityStack.java

private boolean resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options) {
    if (!mService.mBooting && !mService.mBooted) {
        // AMS 还未启动完成
        return false;
    }

    ......

    if (!hasRunningActivity) {
        // 当前 Stack 没有 activity，就去找下一个 stack。可能会启动 Home 应用
        return resumeTopActivityInNextFocusableStack(prev, options, "noMoreActivities");
    }

    // next 就是目标 Activity，将其从下面几个队列移除
    mStackSupervisor.mStoppingActivities.remove(next);
    mStackSupervisor.mGoingToSleepActivities.remove(next);
    next.sleeping = false;
    mStackSupervisor.mActivitiesWaitingForVisibleActivity.remove(next);

    ......

    // mResumedActivity 指当前 Activity
    if (mResumedActivity != null) {
        // 当有其他 Activity 正处于 onResume()，先暂停它
        pausing |= startPausingLocked(userLeaving, false, next, false);
    }

    ......

    ActivityStack lastStack = mStackSupervisor.getLastStack();
    if (next.app != null && next.app.thread != null) {
        ......
        synchronized(mWindowManager.getWindowManagerLock()) {
            // This activity is now becoming visible.
            if (!next.visible || next.stopped || lastActivityTranslucent) {
                next.setVisibility(true);
            }

            ......

            try {
                final ClientTransaction transaction = ClientTransaction.obtain(next.app.thread,
                        next.appToken);
                // Deliver all pending results.
                ArrayList<ResultInfo> a = next.results;
                if (a != null) {
                    final int N = a.size();
                    if (!next.finishing && N > 0) {
                        if (DEBUG_RESULTS) Slog.v(TAG_RESULTS,
                                "Delivering results to " + next + ": " + a);
                        transaction.addCallback(ActivityResultItem.obtain(a));
                    }
                }

                if (next.newIntents != null) {
                    transaction.addCallback(NewIntentItem.obtain(next.newIntents,
                            false /* andPause */));
                }

                next.sleeping = false;
                mService.getAppWarningsLocked().onResumeActivity(next);
                mService.showAskCompatModeDialogLocked(next);
                next.app.pendingUiClean = true;
                next.app.forceProcessStateUpTo(mService.mTopProcessState);
                next.clearOptionsLocked();
                transaction.setLifecycleStateRequest(
                        ResumeActivityItem.obtain(next.app.repProcState,
                                mService.isNextTransitionForward()));
                mService.getLifecycleManager().scheduleTransaction(transaction);

            } catch (Exception e) {
                next.setState(lastState, "resumeTopActivityInnerLocked");

                // lastResumedActivity being non-null implies there is a lastStack present.
                if (lastResumedActivity != null) {
                    lastResumedActivity.setState(RESUMED, "resumeTopActivityInnerLocked");
                }

                Slog.i(TAG, "Restarting because process died: " + next);
                if (!next.hasBeenLaunched) {
                    next.hasBeenLaunched = true;
                } else  if (SHOW_APP_STARTING_PREVIEW && lastStack != null
                        && lastStack.isTopStackOnDisplay()) {
                    next.showStartingWindow(null /* prev */, false /* newTask */,
                            false /* taskSwitch */);
                }
                // 调用 startSpecificActivityLocked()
                mStackSupervisor.startSpecificActivityLocked(next, true, false);
                if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
                return true;
            }
        }

        // From this point on, if something goes wrong there is no way
        // to recover the activity.
        try {
            next.completeResumeLocked();
        } catch (Exception e) {
            ......
        }
    } else {
        ......
        // 调用 startSpecificActivityLocked()
        mStackSupervisor.startSpecificActivityLocked(next, true, true);
    }

    if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
    return true;
}

```

上面省略了 `resumeTopActivityInnerLocked()` 方法中的绝大部分代码，原代码大概有四百多行。其中需要注意的是 `startPausingLocked()` 和 `startSpecificActivityLocked()` 方法。

在启动 Activity 之前，如果当前 Activity 正处于 onResume 状态，那么需要先暂停它，即调用它的 onPause。这就是 `startPausingLocked()` 方法的职责。这里先不具体分析，后面会单独写一篇文章说明 Activity 的声明周期调用。另外多说一句，先要执行当前 Activity 的 onPause 然后才会启动目标 Activity ，所以我们不能在 onPause 中执行耗时任务，会造成切换 Activity 时卡顿。

另一个方法 `startSpecificActivityLocked()` 就是启动指定 Activity 了，我们继续跟下去。


```java
> ActivityStackSupervisor.java

   void startSpecificActivityLocked(ActivityRecord r,
            boolean andResume, boolean checkConfig) {
        // 通过 AMS 查找进程是否已存在
        ProcessRecord app = mService.getProcessRecordLocked(r.processName,
                r.info.applicationInfo.uid, true);

        // 应用进程已经存在并且已经绑定
        if (app != null && app.thread != null) {
            try {
                if ((r.info.flags&ActivityInfo.FLAG_MULTIPROCESS) == 0
                        || !"android".equals(r.info.packageName)) {
                    app.addPackage(r.info.packageName, r.info.applicationInfo.longVersionCode,
                            mService.mProcessStats);
                }
                // 应用进程已存在时调用 realStartActivityLocked()
                realStartActivityLocked(r, app, andResume, checkConfig);
                return;
            } catch (RemoteException e) {
                Slog.w(TAG, "Exception when starting activity "
                        + r.intent.getComponent().flattenToShortString(), e);
            }

            // If a dead object exception was thrown -- fall through to
            // restart the application.
        }

        // 应用进程不存在则创建进程
        mService.startProcessLocked(r.processName, r.info.applicationInfo, true, 0,
                "activity", r.intent.getComponent(), false, false, true);
    }
```

首先通过 AMS 查找应用进程是否已经存在，如果已经存在并且 attach ，则调用 `realStartActivityLocked()` 直接启动目标 Activity 。如果应用进程不存在，则先创建应用进程。

在 [Android 世界中，谁喊醒了 Zygote ？](https://github.com/lulululbj/android_9.0.0_r45) 已经介绍过了应用进程的创建过程。这里再简单说一下，`Zygote` 进程启动时开启了 LocalSocket 服务端，等待客户端请求。AMS 作为 socket 客户端向 Zygote 发出请求，Zygote 收到请求之后 fork 出子进程。

今天看到一个很有意思的提问，**Android 中的 IPC 通信大多通过 Binder 机制实现，为什么 Zygote 通过  socket 跨进程通信？** 说实话，我也不知道，欢迎大家留下你的看法。

接着就是 `realStartActivityLocked()` ，如其名字一样，真正的要启动 Activity 了。


```java
> ActivityStackSupervisor.java

 final boolean realStartActivityLocked(ActivityRecord r, ProcessRecord app,
            boolean andResume, boolean checkConfig) throws RemoteException {

        if (!allPausedActivitiesComplete()) {
            // 直到所有的 onPause() 执行结束才会去启动新的 activity
            return false;
        }

        final TaskRecord task = r.getTask();
        final ActivityStack stack = task.getStack();

        beginDeferResume();

        try {
            ......

            // 更新进程 oom-adj 值
            mService.updateLruProcessLocked(app, true, null);
            mService.updateOomAdjLocked();

            try {

             	......

                // 添加 LaunchActivityItem
                final ClientTransaction clientTransaction = ClientTransaction.obtain(app.thread,
                        r.appToken);
                clientTransaction.addCallback(LaunchActivityItem.obtain(new Intent(r.intent),
                        System.identityHashCode(r), r.info,
                        mergedConfiguration.getGlobalConfiguration(),
                        mergedConfiguration.getOverrideConfiguration(), r.compat,
                        r.launchedFromPackage, task.voiceInteractor, app.repProcState, r.icicle,
                        r.persistentState, results, newIntents, mService.isNextTransitionForward(),
                        profilerInfo));

                // 设置生命周期状态
                final ActivityLifecycleItem lifecycleItem;
                if (andResume) {
                    lifecycleItem = ResumeActivityItem.obtain(mService.isNextTransitionForward());
                } else {
                    lifecycleItem = PauseActivityItem.obtain();
                }
                clientTransaction.setLifecycleStateRequest(lifecycleItem);

                //  重点
                //  // 调用 ClientLifecycleManager.scheduleTransaction()
                mService.getLifecycleManager().scheduleTransaction(clientTransaction);

           		......

            } catch (RemoteException e) {
                if (r.launchFailed) {
                    // 第二次启动失败，finish activity
                    mService.appDiedLocked(app);
                    stack.requestFinishActivityLocked(r.appToken, Activity.RESULT_CANCELED, null,
                            "2nd-crash", false);
                    return false;
                }
                // 第一次失败，重启进程并重试
                r.launchFailed = true;
                app.activities.remove(r);
                throw e;
            }
        } finally {
            endDeferResume();
        }

        r.launchFailed = false;
   		......
        return true;
    }

```
上面的重点是这句代码，`mService.getLifecycleManager().scheduleTransaction(clientTransaction);` 。

这里又用到了 `ClientTransaction` 。还记得上面提到的暂停 Activity 吗 ，也是通过这个类来实现的。本来准备写到生命周期的单独文章再分析，看来还是逃不过。这里穿插着说一下 ClientTransaction 。

首先 `mService.getLifecycleManager()` 返回的是 `ClientLifecycleManager` 对象，这是在 Android 9.0 中新增的类。我们看一下它的 `scheduleTransaction()` 方法。

```java
> ClientLifecycleManager.java

void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
    final IApplicationThread client = transaction.getClient(); // -> ApplicationThread
    transaction.schedule(); // ClientTransaction
    if (!(client instanceof Binder)) {
        transaction.recycle();
    }
}
```

跟进 `schedule()` 方法。

```java
> ClientTransaction.java

public void schedule() throws RemoteException {
    mClient.scheduleTransaction(this);
}
```

这里的 `mClient` 是 IApplicationThread 类型，它是 `ApplicationThread` 的 Binder  代理对象，所以这里会跨进程调用到 `ApplicationThread.scheduleTransaction()`方法 。 `ApplicationThread` 是  `ActivityThread` 的内部类，但不论是 ApplicationThread 还是 ActivityThread 其实都没有 scheduleTransaction() 方法，所以调用的是其父类 `ClientTransactionHandler` 的方法。

```java
> ClientTransactionHandler.java

public abstract class ClientTransactionHandler {

    /** Prepare and schedule transaction for execution. */
    void scheduleTransaction(ClientTransaction transaction) {
        transaction.preExecute(this);
        // sendMessage() 方法在 ActivityThread类中实现
        sendMessage(ActivityThread.H.EXECUTE_TRANSACTION, transaction);
    }

  }
```

在回到 ActivityThread 类中看一下 `sendMessage()` 方法。


```java
> ActivityThread.java

private void sendMessage(int what, Object obj, int arg1, int arg2, boolean async) {
    Message msg = Message.obtain();
    msg.what = what;
    msg.obj = obj;
    msg.arg1 = arg1;
    msg.arg2 = arg2;
    if (async) {
        msg.setAsynchronous(true);
    }
    mH.sendMessage(msg);
}
```

这里向 `mH` 发送了 `EXECUTE_TRANSACTION` 消息，并携带了 transaction 。mH 是一个 叫做 `H` 的 Handler 类。它负责主线程消息处理，定义了大概五十多种事件。查找一下它是如何处理 EXECUTE_TRANSACTION 消息的。

```java
> ActivityThread.java

case EXECUTE_TRANSACTION:
            final ClientTransaction transaction = (ClientTransaction) msg.obj;
           // 执行 TransactionExecutor.execute()
           mTransactionExecutor.execute(transaction);
           if (isSystem()) {
                transaction.recycle();
           }
```

调用了 `TransactionExecutor` 的 `execute()` 方法。

```java
> TransactionExecutor.java`

public void execute(ClientTransaction transaction) {
    final IBinder token = transaction.getActivityToken();
    log("Start resolving transaction for client: " + mTransactionHandler + ", token: " + token);

    // 执行 callBack
    executeCallbacks(transaction);

    // 执行生命周期状态
    executeLifecycleState(transaction);
    mPendingActions.clear();
    log("End resolving transaction");
}
```

先来看看  `executeCallbacks()` 方法。

```java
> TransactionExecutor.java

@VisibleForTesting
public void executeCallbacks(ClientTransaction transaction) {

    ......

    final int size = callbacks.size();
    for (int i = 0; i < size; ++i) {
        final ClientTransactionItem item = callbacks.get(i);

        ......

        item.execute(mTransactionHandler, token, mPendingActions);
        item.postExecute(mTransactionHandler, token, mPendingActions);

        ....
}
```

核心代码就这些。执行传入的 callback 的 `execute()` 方法和 `postExecute()` 方法。还记得之前 `realStartActivityLocked()` 方法中调用  `addCallback()` 传入的参数吗？

```java
clientTransaction.addCallback(LaunchActivityItem.obtain(new Intent(r.intent), ......);
```

也就是说会执行 `LaunchActivityItem` 的 `execute()` 方法。

```java
> LaunchActivityItem.java

@Override
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityStart");
    ActivityClientRecord r = new ActivityClientRecord(token, mIntent, mIdent, mInfo,
            mOverrideConfig, mCompatInfo, mReferrer, mVoiceInteractor, mState, mPersistentState,
            mPendingResults, mPendingNewIntents, mIsForward,
            mProfilerInfo, client);
    // 调用 ActivityThread.handleLaunchActivity()
    client.handleLaunchActivity(r, pendingActions, null /* customIntent */);
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

兜兜转转，再次回到 ActivityThread ，执行其 `handleLaunchActivity()` 方法。

```java
> ActivityThread.java

@Override
public Activity handleLaunchActivity(ActivityClientRecord r,
        PendingTransactionActions pendingActions, Intent customIntent) {

    ......

    final Activity a = performLaunchActivity(r, customIntent);

    ......

    return a;
}
```

```java
> ActivityThread.java

private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
     ActivityInfo aInfo = r.activityInfo;
     if (r.packageInfo == null) {
         r.packageInfo = getPackageInfo(aInfo.applicationInfo, r.compatInfo,
                 Context.CONTEXT_INCLUDE_CODE);
     }

     // 获取 ComponentName
     ComponentName component = r.intent.getComponent();
     if (component == null) {
         component = r.intent.resolveActivity(
             mInitialApplication.getPackageManager());
         r.intent.setComponent(component);
     }

     if (r.activityInfo.targetActivity != null) {
         component = new ComponentName(r.activityInfo.packageName,
                 r.activityInfo.targetActivity);
     }

     // 获取 Context
     ContextImpl appContext = createBaseContextForActivity(r);
     Activity activity = null;
     try {
         java.lang.ClassLoader cl = appContext.getClassLoader();
         // 反射创建 Activity
         activity = mInstrumentation.newActivity(
                 cl, component.getClassName(), r.intent);
         StrictMode.incrementExpectedActivityCount(activity.getClass());
         r.intent.setExtrasClassLoader(cl);
         r.intent.prepareToEnterProcess();
         if (r.state != null) {
             r.state.setClassLoader(cl);
         }
     } catch (Exception e) {
          ......
     }

     try {
         // 获取 Application
         Application app = r.packageInfo.makeApplication(false, mInstrumentation);

         if (activity != null) {
             CharSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());
             Configuration config = new Configuration(mCompatConfiguration);
             if (r.overrideConfig != null) {
                 config.updateFrom(r.overrideConfig);
             }
             Window window = null;
             if (r.mPendingRemoveWindow != null && r.mPreserveWindow) {
                 window = r.mPendingRemoveWindow;
                 r.mPendingRemoveWindow = null;
                 r.mPendingRemoveWindowManager = null;
             }
             appContext.setOuterContext(activity);
             activity.attach(appContext, this, getInstrumentation(), r.token,
                     r.ident, app, r.intent, r.activityInfo, title, r.parent,
                     r.embeddedID, r.lastNonConfigurationInstances, config,
                     r.referrer, r.voiceInteractor, window, r.configCallback);

             if (customIntent != null) {
                 activity.mIntent = customIntent;
             }
             r.lastNonConfigurationInstances = null;
             checkAndBlockForNetworkAccess();
             activity.mStartedActivity = false;
             int theme = r.activityInfo.getThemeResource();
             if (theme != 0) {
                 // 设置主题
                 activity.setTheme(theme);
             }

             activity.mCalled = false;
             // 执行 onCreate()
             if (r.isPersistable()) {
                 mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
             } else {
                 mInstrumentation.callActivityOnCreate(activity, r.state);
             }
             if (!activity.mCalled) {
                 throw new SuperNotCalledException(
                     "Activity " + r.intent.getComponent().toShortString() +
                     " did not call through to super.onCreate()");
             }
             r.activity = activity;
         }
         r.setState(ON_CREATE);

         mActivities.put(r.token, r);

     } catch (SuperNotCalledException e) {
         throw e;

     } catch (Exception e) {
        ......
     }

     return activity;
 }
```

这里又出现了 `Instrumentation` 的身影，分别调用了 `newActivity()` 方法和 `callActivityOnCreate()` 方法。

`newActivity()` 方法反射创建 Activity ，并调用其 `attach()` 方法。

```java
> Instrumentation.java

public Activity newActivity(Class<?> clazz, Context context,
        IBinder token, Application application, Intent intent, ActivityInfo info,
        CharSequence title, Activity parent, String id,
        Object lastNonConfigurationInstance) throws InstantiationException,
        IllegalAccessException {
    Activity activity = (Activity)clazz.newInstance();
    ActivityThread aThread = null;
    // Activity.attach expects a non-null Application Object.
    if (application == null) {
        application = new Application();
    }
    activity.attach(context, aThread, this, token, 0 /* ident */, application, intent,
            info, title, parent, id,
            (Activity.NonConfigurationInstances)lastNonConfigurationInstance,
            new Configuration(), null /* referrer */, null /* voiceInteractor */,
            null /* window */, null /* activityConfigCallback */);
    return activity;
}
```

`callActivityOnCreate()` 方法调用 `Activity.performCreate()` 方法，最终回调 `onCreate()` 方法。

```java
> Instrumentation.java

public void callActivityOnCreate(Activity activity, Bundle icicle) {
    prePerformCreate(activity);
    activity.performCreate(icicle);
    postPerformCreate(activity);
}
```

```java
> Activity.java

final void performCreate(Bundle icicle) {
    performCreate(icicle, null);
}

final void performCreate(Bundle icicle, PersistableBundle persistentState) {
    mCanEnterPictureInPicture = true;
    restoreHasCurrentPermissionRequest(icicle);
    // 回调 onCreate()
    if (persistentState != null) {
        onCreate(icicle, persistentState);
    } else {
        onCreate(icicle);
    }
    writeEventLog(LOG_AM_ON_CREATE_CALLED, "performCreate");
    mActivityTransitionState.readState(icicle);

    mVisibleFromClient = !mWindow.getWindowStyle().getBoolean(
            com.android.internal.R.styleable.Window_windowNoDisplay, false);
    mFragments.dispatchActivityCreated();
    mActivityTransitionState.setEnterActivityOptions(this, getActivityOptions());
}
```

看到这里，有一种如释重负的感觉，终于执行到  **onCreate()** 方法了。其实 Activity 的每个生命周期回调都是类似的调用链。

还记得是从哪个方法一路追踪到 onCreate 的吗？是  `TransactionExecutor` 的 `execute()` 方法。

```java
> TransactionExecutor.java`

public void execute(ClientTransaction transaction) {
    final IBinder token = transaction.getActivityToken();
    log("Start resolving transaction for client: " + mTransactionHandler + ", token: " + token);

    // 执行 callBack
    executeCallbacks(transaction);

    // 执行生命周期状态
    executeLifecycleState(transaction);
    mPendingActions.clear();
    log("End resolving transaction");
}
```

前面分析 `executeCallBack()` 一路追踪到 onCreate() ，接下来就要分析 `executeLifecycleState()` 方法了。

```java
> TransactionExecutor.java

private void executeLifecycleState(ClientTransaction transaction) {
     final ActivityLifecycleItem lifecycleItem = transaction.getLifecycleStateRequest();
     if (lifecycleItem == null) {
         // No lifecycle request, return early.
         return;
     }

     final IBinder token = transaction.getActivityToken();
     final ActivityClientRecord r = mTransactionHandler.getActivityClient(token);

     if (r == null) {
         // Ignore requests for non-existent client records for now.
         return;
     }

     // Cycle to the state right before the final requested state.
     cycleToPath(r, lifecycleItem.getTargetState(), true /* excludeLastState */);

     // Execute the final transition with proper parameters.
     lifecycleItem.execute(mTransactionHandler, token, mPendingActions);
     lifecycleItem.postExecute(mTransactionHandler, token, mPendingActions);
 }

```

很熟悉，又看到了 `lifecycleItem.execute()` 。这里的 `lifecycleItem` 还是在 `realStartActivityLocked()` 方法中赋值的。

```java
  lifecycleItem = ResumeActivityItem.obtain(mService.isNextTransitionForward());
```

但在分析 `ResumeActivityItem` 之前，注意一下 `execute()` 方法之前的 `cycleToPath()` 方法。具体源码就不去分析了，它的作用时根据上次最后执行到的生命周期状态，和即将执行的生命周期状态进行同步。说的不是那么容易理解，举个例子，上次已经回调了 onCreate() 方法，这次要执行的是 `ResumeActivityItem` ，中间还有一个 `onStart()` 状态，那么 `cycleToPath()` 方法就会去回调 `onStart()` ，也就是调用 `ActivityThread.handleStartActivity()` 。和 `handleLaunchActivity()` 差不多的调用链。

那么，再回到 `ResumeActivityItem.execute()` 。

```java
> ResumeActivityItem.java

@Override
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityResume");
    client.handleResumeActivity(token, true /* finalStateRequest */, mIsForward,
            "RESUME_ACTIVITY");
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

依旧是调用 `ActivityThread.handleResumeActivity()` 。不过这里有一点比较特殊，还是得拎出来说一下。

```java
> ActivityThread.java

public void handleResumeActivity(IBinder token, boolean finalStateRequest, boolean isForward,
         String reason) {
        ......

         r.activity.mVisibleFromServer = true;
         mNumVisibleActivities++;
         if (r.activity.mVisibleFromClient) {
            // 页面可见
             r.activity.makeVisible();
         }
     }

     // 主线程空闲时会执行 Idler
     Looper.myQueue().addIdleHandler(new Idler());
 }
```

`makeVisible()` 方法让 DecorView 可见。

```java
> Activity.java

void makeVisible() {
    if (!mWindowAdded) {
        ViewManager wm = getWindowManager();
        wm.addView(mDecor, getWindow().getAttributes());
        mWindowAdded = true;
    }
    mDecor.setVisibility(View.VISIBLE);
}
```

最后要注意的就是 `Looper.myQueue().addIdleHandler(new Idler())`。由于篇幅原因，这里先不介绍了，后面单独写 Activity 生命周期的时候再做分析。大家可以先去源码中找找答案。

## 总结

一路分析过来，Activity终于展示给用户了。

文章其实又臭又长，很多人可能会有疑问，看这些真的有用吗？在我看来，一个程序员最重要的两样东西就是基本功和内功。良好的基本功可以让我们轻松上手一门技术，而深厚的内功就可以让我们面对难题迎刃而解。源码能带给你的，正是这些。

最近看了 Jetpack 中一些组件的源码，下一篇文章应该就是Jetpack 相关了。敬请期待！
