# 细数 SharedPreferences 的那些槽点 ！
## 前言

最近在处理一个历史遗留项目的时候饱受其害，主要表现为偶发性的 SharedPreferences 配置文件数据错乱，甚至丢失。经过排查发现是多进程的问题。项目中有两个不同进程，且会频繁的读写 SharedPreferences 文件，所以导致了数据错乱和丢失。趁此机会，精读了一遍 SharedPreferences 源码，下面就来说说 SharedPreferences 都有哪些槽点。

## 源码解析

SharedPreferences 的使用很简单，这里就不再演示了。下面就按 **获取 SharedPreference** 、**getXXX() 获取数据** 和 **putXXX()存储数据** 这三方面来阅读源码。

### 1. 获取 SharedPreferences

#### 1.1 getDefaultSharedPreferences()
一般我们会通过 `PreferenceManager` 的 `getDefaultSharedPreferences()` 方法来获取默认的 `SharedPreferences` 对象，其代码如下所示：

```java
> PreferenceManager.java 

/**
 * 获取默认的 SharedPreferences 对象，文件名为 packageName_preferences , mode 为 MODE_PRIVATE
 */
public static SharedPreferences getDefaultSharedPreferences(Context context) {
    return context.getSharedPreferences(getDefaultSharedPreferencesName(context),
            getDefaultSharedPreferencesMode());  // 见 1.2
}
```

默认的 sp 文件完整路径为 `/data/data/shared_prefs/[packageName]_preferences.xml`。`mode` 默认为 `MODE_PRIVATE`，其实现在也只用这种模式了，后面的源码解析中也会提到。最后都会调用到 `ContextImpl` 的 `getSharedPreferences()` 方法。

#### 1.2 getSharedPreferences(String name, int mode)
```java
> ContextImpl.java

@Override
public SharedPreferences getSharedPreferences(String name, int mode) {
    // At least one application in the world actually passes in a null
    // name.  This happened to work because when we generated the file name
    // we would stringify it to "null.xml".  Nice.
    if (mPackageInfo.getApplicationInfo().targetSdkVersion <
            Build.VERSION_CODES.KITKAT) {
        if (name == null) {
            name = "null";
        }
    }

    File file;
    synchronized (ContextImpl.class) {
        if (mSharedPrefsPaths == null) {
            mSharedPrefsPaths = new ArrayMap<>();
        }
        // 先从缓存 mSharedPrefsPaths 中查找 sp 文件是否存在
        file = mSharedPrefsPaths.get(name);
        if (file == null) { // 如果不存在，新建 sp 文件，文件名为 "name.xml"
            file = getSharedPreferencesPath(name);
            mSharedPrefsPaths.put(name, file);
        }
    }
    return getSharedPreferences(file, mode); // 见 1.3
}
```

首先这里出现了一个变量 `mSharedPrefsPaths`，找一下它的定义：

```java
/**
 * 文件名为 key，具体文件为 value。存储所有 sp 文件
 * 由 ContextImpl.class 锁保护
 */
@GuardedBy("ContextImpl.class")
private ArrayMap<String, File> mSharedPrefsPaths;
```

`mSharedPrefsPaths` 是一个 ArrayMap ，缓存了文件名和 sp 文件的对应关系。首先会根据参数中的文件名 `name` 查找缓存中是否存在对应的 sp 文件。如果不存在的话，会新建名称为 `[name].xml` 的文件，并存入缓存 `mSharedPrefsPaths` 中。最后会调用另一个重载的 `getSharedPreferences()` 方法，参数是 File 。

#### 1.3 getSharedPreferences(File file, int mode)

```java
> ContextImpl.java

@Override
public SharedPreferences getSharedPreferences(File file, int mode) {
    SharedPreferencesImpl sp;
    synchronized (ContextImpl.class) {
        final ArrayMap<File, SharedPreferencesImpl> cache = getSharedPreferencesCacheLocked(); // 见 1.3.1
        sp = cache.get(file); // 先从缓存中尝试获取 sp
        if (sp == null) { // 如果获取缓存失败
            checkMode(mode); // 检查 mode，见 1.3.2
            if (getApplicationInfo().targetSdkVersion >= android.os.Build.VERSION_CODES.O) {
                if (isCredentialProtectedStorage()
                        && !getSystemService(UserManager.class)
                                .isUserUnlockingOrUnlocked(UserHandle.myUserId())) {
                    throw new IllegalStateException("SharedPreferences in credential encrypted "
                            + "storage are not available until after user is unlocked");
                }
            }
            sp = new SharedPreferencesImpl(file, mode); // 创建 SharedPreferencesImpl，见 1.4
            cache.put(file, sp);
            return sp;
        }
    }

    // mode 为 MODE_MULTI_PROCESS 时，文件可能被其他进程修改，则重新加载
    // 显然这并不足以保证跨进程安全
    if ((mode & Context.MODE_MULTI_PROCESS) != 0 ||
        getApplicationInfo().targetSdkVersion < android.os.Build.VERSION_CODES.HONEYCOMB) {
        // If somebody else (some other process) changed the prefs
        // file behind our back, we reload it.  This has been the
        // historical (if undocumented) behavior.
        sp.startReloadIfChangedUnexpectedly();
    }
    return sp;
}
```

`SharedPreferences` 只是接口而已，我们要获取的实际上是它的实现类 `SharedPreferencesImpl` 。通过 `getSharedPreferencesCacheLocked()` 方法可以获取已经缓存的 SharedPreferencesImpl 对象和其 sp 文件。

##### 1.3.1 getSharedPreferencesCacheLocked()

```java
> ContextImpl.java

private ArrayMap<File, SharedPreferencesImpl> getSharedPreferencesCacheLocked() {
    if (sSharedPrefsCache == null) {
        sSharedPrefsCache = new ArrayMap<>();
    }

    final String packageName = getPackageName();
    ArrayMap<File, SharedPreferencesImpl> packagePrefs = sSharedPrefsCache.get(packageName);
    if (packagePrefs == null) {
        packagePrefs = new ArrayMap<>();
        sSharedPrefsCache.put(packageName, packagePrefs);
    }

    return packagePrefs;
}
```

`sSharedPrefsCache` 是一个嵌套的 ArrayMap，其定义如下：

```java
private static ArrayMap<String, ArrayMap<File, SharedPreferencesImpl>> sSharedPrefsCache;
```

以包名为 key ，以一个存储了 sp 文件及其 SharedPreferencesImp 对象的 ArrayMap 为 value。如果存在直接返回，反之创建一个新的 ArrayMap 作为值并存入缓存。

##### 1.3.2 checkMode()

```java
> ContextImpl.java

private void checkMode(int mode) {
    // 从 N 开始，如果使用 MODE_WORLD_READABLE 和 MODE_WORLD_WRITEABLE，直接抛出异常
    if (getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.N) {
        if ((mode & MODE_WORLD_READABLE) != 0) {
            throw new SecurityException("MODE_WORLD_READABLE no longer supported");
        }
        if ((mode & MODE_WORLD_WRITEABLE) != 0) {
            throw new SecurityException("MODE_WORLD_WRITEABLE no longer supported");
        }
    }
}
```

从 Android N 开始，明确不再支持 `MODE_WORLD_READABLE` 和 `MODE_WORLD_WRITEABLE`，再加上 `MODE_MULTI_PROCESS` 并不能保证线程安全，一般就使用 `MODE_PRIVATE` 就可以了。

#### 1.4 SharedPreferencesImpl

如果缓存中没有对应的 `SharedPreferencesImpl` 对象，就得自己创建了。看一下它的构造函数：

```java
SharedPreferencesImpl(File file, int mode) {
    mFile = file; // sp 文件
    mBackupFile = makeBackupFile(file); // 创建备份文件
    mMode = mode; 
    mLoaded = false; // 标识 sp 文件是否已经加载到内存
    mMap = null; // 存储 sp 文件中的键值对
    mThrowable = null;
    startLoadFromDisk(); // 加载数据，见 1.4.1
}
```

注意这里的 `mMap`，它是一个 `Map<String, Object>`，存储了 sp 文件中的所有键值对。所以 SharedPreferences 文件的所有数据都是存在于内存中的，既然存在于内存中，就注定它不适合存储大量数据。

##### 1.4.1 startLoadFromDisk()

```java
> SharedPreferencesImpl.java

private void startLoadFromDisk() {
    synchronized (mLock) {
        mLoaded = false;
    }
    new Thread("SharedPreferencesImpl-load") {
        public void run() {
            loadFromDisk(); // 异步加载。 见 1.4.2
        }
    }.start();
}
```

##### 1.4.2  loadFromDisk()

```java
> SharedPreferencesImpl.java

private void loadFromDisk() {
    synchronized (mLock) { // 获取 mLock 锁
        if (mLoaded) { // 已经加载进内存，直接返回，不再读取文件
            return;
        }
        if (mBackupFile.exists()) { // 如果存在备份文件，直接将备份文件重命名为 sp 文件
            mFile.delete();
            mBackupFile.renameTo(mFile);
        }
    }

    // Debugging
    if (mFile.exists() && !mFile.canRead()) {
        Log.w(TAG, "Attempt to read preferences file " + mFile + " without permission");
    }

    Map<String, Object> map = null;
    StructStat stat = null;
    Throwable thrown = null;
    try { // 读取 sp 文件
        stat = Os.stat(mFile.getPath());
        if (mFile.canRead()) {
            BufferedInputStream str = null;
            try {
                str = new BufferedInputStream(
                        new FileInputStream(mFile), 16 * 1024);
                map = (Map<String, Object>) XmlUtils.readMapXml(str);
            } catch (Exception e) {
                Log.w(TAG, "Cannot read " + mFile.getAbsolutePath(), e);
            } finally {
                IoUtils.closeQuietly(str);
            }
        }
    } catch (ErrnoException e) {
        // An errno exception means the stat failed. Treat as empty/non-existing by
        // ignoring.
    } catch (Throwable t) {
        thrown = t;
    }

    synchronized (mLock) {
        mLoaded = true;
        mThrowable = thrown;

        // It's important that we always signal waiters, even if we'll make
        // them fail with an exception. The try-finally is pretty wide, but
        // better safe than sorry.
        try {
            if (thrown == null) {
                if (map != null) {
                    mMap = map;
                    mStatTimestamp = stat.st_mtim; // 更新修改时间
                    mStatSize = stat.st_size; // 更新文件大小
                } else {
                    mMap = new HashMap<>();
                }
            }
            // In case of a thrown exception, we retain the old map. That allows
            // any open editors to commit and store updates.
        } catch (Throwable t) {
            mThrowable = t;
        } finally {
            mLock.notifyAll(); // 唤醒处于等待状态的线程
        }
    }
}
```

简单捋一下流程：

1. 判断是否已经加载进内存
2. 判断是否存在遗留的备份文件，如果存在，重命名为 sp 文件
3. 读取 sp 文件，并存入内存
4. 更新文件信息
5. 释放锁，唤醒处于等待状态的线程

`loadFromDisk()` 是异步执行的，而且是线程安全的，读取过程中持有锁 `mLock`，看起来设计的都很合理，但是在不合理的使用情况下就会出现问题。

看了这么长的源码，别忘了我们还停留在 `getSharedPreferences()` 方法，也就是获取 SharedPreferences 的过程中。如果我们在使用过程中，调用 `getSharedPreferences()` 之后，直接调用 `getXXX()` 方法来获取数据，恰好 sp 文件数据量又比较大，读取过程比较耗时，`getXXX()` 方法就会被阻塞。后面看到 `getXXX()` 方法的源码时，你就会看到它需要等待 sp 文件加载完成，否则就会阻塞。所以在使用过程中，可以提前异步初始化 SharedPreferences 对象，加载 sp 文件进内存，避免发生潜在可能的卡顿。这是 SharedPreferences 的一个槽点，也是我们使用过程中需要注意的。

### 2. 读取 sp 数据

获取 sp 文件中的数据使用的是 `SharedPreferencesImpl` 中的七个 `getXXX` 函数。这七个函数都是一样的逻辑，以 `getInt()` 为例看一下源码：

```java
> SharedPreferencesImpl.java

@Override
public int getInt(String key, int defValue) {
    synchronized (mLock) {
        awaitLoadedLocked(); // sp 文件尚未加载完成时，会阻塞在这里，见 2.1
        Integer v = (Integer)mMap.get(key); // 加载完成后直接从内存中读取
        return v != null ? v : defValue;
    }
}
```

一旦 sp 文件加载完成，所有获取数据的操作都是从内存中读取的。这样的确提升了效率，但是很显然将大量的数据直接放在内存是不合适的，所以注定了 SharedPreferences 不适合存储大量数据。

#### 2.1 awaitLoadedLocked()

```java
> SharedPreferencesImpl.java

@GuardedBy("mLock")
private void awaitLoadedLocked() {
    if (!mLoaded) {
        // Raise an explicit StrictMode onReadFromDisk for this
        // thread, since the real read will be in a different
        // thread and otherwise ignored by StrictMode.
        BlockGuard.getThreadPolicy().onReadFromDisk();
    }
    while (!mLoaded) { // sp 文件尚未加载完成时, 等待
        try {
            mLock.wait();
        } catch (InterruptedException unused) {
        }
    }
    if (mThrowable != null) {
        throw new IllegalStateException(mThrowable);
    }
}
```

`mLoaded` 初始值为 `false`，在 `loadFromDisk()` 方法中读取 sp 文件之后会被置为 `true`，并调用 `mLock.notifyAll()` 通知等待的线程。

### 3. 存储 sp 数据

SharedPreferences 存储数据的基本方法如下：

```kotlin
val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
editor.putInt("key",1)
editor.commit()/editor.apply()
```

`edit()` 方法会返回一个 `Editor()` 对象。`Editor` 和 `SharedPreferences` 一样，都只是接口，它们的实现类分别是 `EditorImpl` 和 `SharedPreferencesImpl`。

#### 3.1 edit()

```java
> SharedPreferencesImpl.java

@Override
public Editor edit() {
    synchronized (mLock) {
        awaitLoadedLocked(); // 等待 sp 文件加载完成
    }

    return new EditorImpl(); // 见 3.2
}
```

`edit()` 方法同样也要等待 sp 文件加载完成，再进行 `EditImpl()` 的初始化。每次调用 `edit()` 方法都会实例化一个新的 `EditorImpl` 对象。所以我们在使用的时候要注意不要每次 put() 都去调用 `edit()` 方法，在封装 SharedPreferences 工具类的时候可能会犯这个错误。

#### 3.2 EditorImpl

```java
> SharedPreferencesImpl.java

public final class EditorImpl implements Editor {
    private final Object mEditorLock = new Object();

    @GuardedBy("mEditorLock")
    private final Map<String, Object> mModified = new HashMap<>(); // 存储要修改的数据

    @GuardedBy("mEditorLock")
    private boolean mClear = false; // 清除标记

    @Override
    public Editor putString(String key, @Nullable String value) {
        synchronized (mEditorLock) {
            mModified.put(key, value);
            return this;
        }
    }
    
    @Override
    public Editor remove(String key) {
        synchronized (mEditorLock) {
            mModified.put(key, this);
            return this;
        }
    }

    @Override
    public Editor clear() {
        synchronized (mEditorLock) {
            mClear = true;
            return this;
        }
    }
    
    @Override
    public boolean commit() { } // 见 3.2.1
    
    @Override
    public boolean apply() { } // 见 3.2.2
```

有两个成员变量，`mModified` 和 `mClear`。`mModified` 是一个 `HashMap`，存储了所有通过 `putXXX()` 方法添加的需要添加或者修改的键值对。`mClear` 是清除标记，在 `clear()` 方法中会被置为 `true`。

所有的 `putXXX()` 方法都只是改变了 `mModified` 集合，当调用 `commit()` 或者 `apply()` 时才会去修改 sp 文件。下面分别看一下这两个方法。

##### 3.2.1 commit()

```java
> SharedPreferencesImpl.java

@Override
    public boolean commit() {
        long startTime = 0;

        if (DEBUG) {
            startTime = System.currentTimeMillis();
        }

        // 先将 mModified 同步到内存
        MemoryCommitResult mcr = commitToMemory(); // 见 3.2.2

        // 再将内存数据同步到文件，见 3.2.3
        SharedPreferencesImpl.this.enqueueDiskWrite(
            mcr, null /* sync write on this thread okay */);
        try {
            mcr.writtenToDiskLatch.await(); // 等待写入操作完成
        } catch (InterruptedException e) {
            return false;
        } finally {
            if (DEBUG) {
                Log.d(TAG, mFile.getName() + ":" + mcr.memoryStateGeneration
                        + " committed after " + (System.currentTimeMillis() - startTime)
                        + " ms");
            }
        }
        notifyListeners(mcr); // 通知监听者，回调 OnSharedPreferenceChangeListener
        return mcr.writeToDiskResult; // 返回写入操作结果
    }
```

`commit()` 的大致流程是：

* 首先同步 `mModified` 到内存中 ,  commitToMemory()
* 然后同步内存数据到 sp 文件中 ，enqueueDiskWrite()
* 等待写入操作完成，并通知监听者

内存同步是 `commitToMemory()` 方法，写入文件是 `enqueueDiskWrite()` 方法。来详细看一下这两个方法。

##### 3.2.2 commitToMemory()

```java
> SharedPreferencesImpl.java

// Returns true if any changes were made
private MemoryCommitResult commitToMemory() {
    long memoryStateGeneration;
    List<String> keysModified = null;
    Set<OnSharedPreferenceChangeListener> listeners = null;
    Map<String, Object> mapToWriteToDisk;

    synchronized (SharedPreferencesImpl.this.mLock) {
        // 在 commit() 的写入本地文件过程中，会将 mDiskWritesInFlight 置为 1.
        // 写入过程尚未完成时，又调用了 commitToMemory()，直接修改 mMap 可能会影响写入结果
        // 所以这里要对 mMap 进行一次深拷贝
        if (mDiskWritesInFlight > 0) {   
            mMap = new HashMap<String, Object>(mMap);
        }
        mapToWriteToDisk = mMap;
        mDiskWritesInFlight++;

        boolean hasListeners = mListeners.size() > 0;
        if (hasListeners) {
            keysModified = new ArrayList<String>();
            listeners = new HashSet<OnSharedPreferenceChangeListener>(mListeners.keySet());
        }

        synchronized (mEditorLock) {
            boolean changesMade = false;

            if (mClear) {
                if (!mapToWriteToDisk.isEmpty()) {
                    changesMade = true;
                    mapToWriteToDisk.clear();
                }
                mClear = false;
            }

            for (Map.Entry<String, Object> e : mModified.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                // "this" is the magic value for a removal mutation. In addition,
                // setting a value to "null" for a given key is specified to be
                // equivalent to calling remove on that key.
                // v == this 和 v == null 都表示删除此 key
                if (v == this || v == null) {
                    if (!mapToWriteToDisk.containsKey(k)) {
                        continue;
                    }
                    mapToWriteToDisk.remove(k);
                } else {
                    if (mapToWriteToDisk.containsKey(k)) {
                        Object existingValue = mapToWriteToDisk.get(k);
                        if (existingValue != null && existingValue.equals(v)) {
                            continue;
                        }
                    }
                    mapToWriteToDisk.put(k, v);
                }

                changesMade = true;
                if (hasListeners) {
                    keysModified.add(k);
                }
            }

            mModified.clear();

            if (changesMade) {
                mCurrentMemoryStateGeneration++;
            }

            memoryStateGeneration = mCurrentMemoryStateGeneration;
        }
    }
    return new MemoryCommitResult(memoryStateGeneration, keysModified, listeners,
            mapToWriteToDisk);
}
```

简单说，`commitToMemory()` 方法会将所有需要改动的数据 `mModified` 和原 sp 文件数据 `mMap` 进行合并生成一个新的数据集合 `mapToWriteToDisk`,从名字也可以看出来，这就是之后要写入文件的数据集。没错，SharedPreferences 的写入都是全量写入。即使你只改动了其中一个配置项，也会重新写入所有数据。针对这一点，我们可以做的优化是，将需要频繁改动的配置项使用单独的 sp 文件进行存储，避免每次都要全量写入。

##### 3.2.3 enqueueDiskWrite()

```java
> SharedPreferencesImpl.java

private void enqueueDiskWrite(final MemoryCommitResult mcr,
                                final Runnable postWriteRunnable) {
    final boolean isFromSyncCommit = (postWriteRunnable == null);

    final Runnable writeToDiskRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mWritingToDiskLock) {
                writeToFile(mcr, isFromSyncCommit); // 见 3.2.3.1
            }
            synchronized (mLock) {
                mDiskWritesInFlight--;
            }
            if (postWriteRunnable != null) {
                postWriteRunnable.run();
            }
        }
    };

    // Typical #commit() path with fewer allocations, doing a write on
    // the current thread.
    // commit() 直接在当前线程进行写入操作
    if (isFromSyncCommit) {
        boolean wasEmpty = false;
        synchronized (mLock) {
            wasEmpty = mDiskWritesInFlight == 1;
        }
        if (wasEmpty) {
            writeToDiskRunnable.run();
            return;
        }
    }

    // apply() 方法执行此处，由 QueuedWork.QueuedWorkHandler 处理
    QueuedWork.queue(writeToDiskRunnable, !isFromSyncCommit);
}
```

回头先看一下 `commit()` 方法中是如何调用 `enqueueDiskWrite()` 方法的：

```java
 SharedPreferencesImpl.this.enqueueDiskWrite(mcr, null);
```

第二个参数 `postWriteRunnable` 是 `null`，所以 `isFromSyncCommit` 为 `true`，会执行上面的 `if` 代码块，而不执行 ` QueuedWork.queue()`。由此可见，`commit()` 方法最后的写文件操作是直接在当前调用线程执行的，你在主线程调用该方法，就会直接在主线程进行 IO 操作。显然，这是不建议的，可能造成卡顿或者 ANR。在实际使用中我们应该尽量使用 `apply()` 方法来提交数据。当然，`apply()` 也并不是十全十美的，后面我们会提到。

###### 3.2.3.1 writeToFile()

`commit()` 方法的最后一步了，将 `mapToWriteToDisk` 写入 sp 文件。

```java
> SharedPreferencesImpl.java

private void writeToFile(MemoryCommitResult mcr, boolean isFromSyncCommit) {
        long startTime = 0;
        long existsTime = 0;
        long backupExistsTime = 0;
        long outputStreamCreateTime = 0;
        long writeTime = 0;
        long fsyncTime = 0;
        long setPermTime = 0;
        long fstatTime = 0;
        long deleteTime = 0;

        if (DEBUG) {
            startTime = System.currentTimeMillis();
        }

        boolean fileExists = mFile.exists();

        if (DEBUG) {
            existsTime = System.currentTimeMillis();

            // Might not be set, hence init them to a default value
            backupExistsTime = existsTime;
        }

        // Rename the current file so it may be used as a backup during the next read
        if (fileExists) {
            boolean needsWrite = false;

            // Only need to write if the disk state is older than this commit
            // 仅当磁盘状态比当前提交旧时草需要写入文件
            if (mDiskStateGeneration < mcr.memoryStateGeneration) {
                if (isFromSyncCommit) {
                    needsWrite = true;
                } else {
                    synchronized (mLock) {
                        // No need to persist intermediate states. Just wait for the latest state to
                        // be persisted.
                        if (mCurrentMemoryStateGeneration == mcr.memoryStateGeneration) {
                            needsWrite = true;
                        }
                    }
                }
            }

            if (!needsWrite) { // 无需写入，直接返回
                mcr.setDiskWriteResult(false, true);
                return;
            }

            boolean backupFileExists = mBackupFile.exists(); // 备份文件是否存在

            if (DEBUG) {
                backupExistsTime = System.currentTimeMillis();
            }

            // 如果备份文件不存在，将 mFile 重命名为备份文件，供以后遇到异常时使用
            if (!backupFileExists) {
                if (!mFile.renameTo(mBackupFile)) {
                    Log.e(TAG, "Couldn't rename file " + mFile
                          + " to backup file " + mBackupFile);
                    mcr.setDiskWriteResult(false, false);
                    return;
                }
            } else {
                mFile.delete();
            }
        }

        // Attempt to write the file, delete the backup and return true as atomically as
        // possible.  If any exception occurs, delete the new file; next time we will restore
        // from the backup.
        try {
            FileOutputStream str = createFileOutputStream(mFile);

            if (DEBUG) {
                outputStreamCreateTime = System.currentTimeMillis();
            }

            if (str == null) {
                mcr.setDiskWriteResult(false, false);
                return;
            }
            XmlUtils.writeMapXml(mcr.mapToWriteToDisk, str); // 全量写入

            writeTime = System.currentTimeMillis();

            FileUtils.sync(str);

            fsyncTime = System.currentTimeMillis();

            str.close();
            ContextImpl.setFilePermissionsFromMode(mFile.getPath(), mMode, 0);

            if (DEBUG) {
                setPermTime = System.currentTimeMillis();
            }

            try {
                final StructStat stat = Os.stat(mFile.getPath());
                synchronized (mLock) {
                    mStatTimestamp = stat.st_mtim; // 更新文件时间
                    mStatSize = stat.st_size; // 更新文件大小
                }
            } catch (ErrnoException e) {
                // Do nothing
            }

            if (DEBUG) {
                fstatTime = System.currentTimeMillis();
            }

            // Writing was successful, delete the backup file if there is one.
            // 写入成功，删除备份文件
            mBackupFile.delete();

            if (DEBUG) {
                deleteTime = System.currentTimeMillis();
            }

            mDiskStateGeneration = mcr.memoryStateGeneration;

            // 返回写入成功，唤醒等待线程
            mcr.setDiskWriteResult(true, true);

            if (DEBUG) {
                Log.d(TAG, "write: " + (existsTime - startTime) + "/"
                        + (backupExistsTime - startTime) + "/"
                        + (outputStreamCreateTime - startTime) + "/"
                        + (writeTime - startTime) + "/"
                        + (fsyncTime - startTime) + "/"
                        + (setPermTime - startTime) + "/"
                        + (fstatTime - startTime) + "/"
                        + (deleteTime - startTime));
            }

            long fsyncDuration = fsyncTime - writeTime;
            mSyncTimes.add((int) fsyncDuration);
            mNumSync++;

            if (DEBUG || mNumSync % 1024 == 0 || fsyncDuration > MAX_FSYNC_DURATION_MILLIS) {
                mSyncTimes.log(TAG, "Time required to fsync " + mFile + ": ");
            }

            return;
        } catch (XmlPullParserException e) {
            Log.w(TAG, "writeToFile: Got exception:", e);
        } catch (IOException e) {
            Log.w(TAG, "writeToFile: Got exception:", e);
        }

        // Clean up an unsuccessfully written file
        // 清除未成功写入的文件
        if (mFile.exists()) {
            if (!mFile.delete()) {
                Log.e(TAG, "Couldn't clean up partially-written file " + mFile);
            }
        }
        mcr.setDiskWriteResult(false, false); // 返回写入失败
    }
```

流程比较清晰，代码也比较简单，

##### 3.2.4 apply()

```java
> SharedPreferencesImpl.java

@Override
public void apply() {
    final long startTime = System.currentTimeMillis();

    // 先将 mModified 同步到内存
    final MemoryCommitResult mcr = commitToMemory();
    final Runnable awaitCommit = new Runnable() {
            @Override
            public void run() {
                try {
                    mcr.writtenToDiskLatch.await();
                } catch (InterruptedException ignored) {
                }

                if (DEBUG && mcr.wasWritten) {
                    Log.d(TAG, mFile.getName() + ":" + mcr.memoryStateGeneration
                            + " applied after " + (System.currentTimeMillis() - startTime)
                            + " ms");
                }
            }
        };

    QueuedWork.addFinisher(awaitCommit);

    Runnable postWriteRunnable = new Runnable() {
            @Override
            public void run() {
                awaitCommit.run();
                QueuedWork.removeFinisher(awaitCommit);
            }
        };

    SharedPreferencesImpl.this.enqueueDiskWrite(mcr, postWriteRunnable);

    // Okay to notify the listeners before it's hit disk
    // because the listeners should always get the same
    // SharedPreferences instance back, which has the
    // changes reflected in memory.
    notifyListeners(mcr);
}
```

同样也是先调用 `commitToMemory()` 同步到内存，再调用 `enqueueDiskWrite()` 同步到文件。和 `commit()` 不同的是，`enqueueDiskWrite()` 方法的 Runnable 参数不再是 null 了，传进来一个 `postWriteRunnable` 。所以其内部的执行逻辑和 `commit()` 方法是完全不同的。可以再回到 3.2.3 节看一下，`commit()` 方法会直接在当前线程执行 `writeToDiskRunnable()`，而 `apply()` 会由 `QueuedWork` 来处理：

```java
QueuedWork.queue(writeToDiskRunnable, !isFromSyncCommit); // 见 3.2.5
```

##### 3.2.5 queue()

```java
> QueuedWork.java

public static void queue(Runnable work, boolean shouldDelay) {
    Handler handler = getHandler();

    synchronized (sLock) {
        sWork.add(work);

        if (shouldDelay && sCanDelay) {
            handler.sendEmptyMessageDelayed(QueuedWorkHandler.MSG_RUN, DELAY);
        } else {
            handler.sendEmptyMessage(QueuedWorkHandler.MSG_RUN);
        }
    }
}
```

这里的 `handler` 所在的线程就是执行 Runnable 的线程了，看一下 `getHandler` 源码：

```java
> QueuedWork.java

private static Handler getHandler() {
    synchronized (sLock) {
        if (sHandler == null) {
            HandlerThread handlerThread = new HandlerThread("queued-work-looper",
                    Process.THREAD_PRIORITY_FOREGROUND);
            handlerThread.start();

            sHandler = new QueuedWorkHandler(handlerThread.getLooper());
        }
        return sHandler;
    }
}
```

写 sp 文件的操作会异步执行在一个单独的线程上。

`QueuedWork` 除了执行异步操作之外，还有一个作用。它可以确保当 Activity `onPause()/onStop()` 之后，或者 BroadCast `onReceive()` 之后，异步任务可以执行完成。以 `ActivityThread.java` 中 `handlePauseActivity()` 方法为例：

```java
@Override
public void handleStopActivity(IBinder token, boolean show, int configChanges,
        PendingTransactionActions pendingActions, boolean finalStateRequest, String reason) {
    final ActivityClientRecord r = mActivities.get(token);
    r.activity.mConfigChangeFlags |= configChanges;

    final StopInfo stopInfo = new StopInfo();
    performStopActivityInner(r, stopInfo, show, true /* saveState */, finalStateRequest,
            reason);

    if (localLOGV) Slog.v(
        TAG, "Finishing stop of " + r + ": show=" + show
        + " win=" + r.window);

    updateVisibility(r, show);

    // Make sure any pending writes are now committed.
    // 可能因等待写入造成卡顿甚至 ANR
    if (!r.isPreHoneycomb()) {
        QueuedWork.waitToFinish();
    }

    stopInfo.setActivity(r);
    stopInfo.setState(r.state);
    stopInfo.setPersistentState(r.persistentState);
    pendingActions.setStopInfo(stopInfo);
    mSomeActivitiesChanged = true;
}
```

初衷可能是好的，但是我们都知道在 Activity() 的 `onPause()/onStop()` 中不应该进行耗时任务。如果 sp 数据量很大的话，这里无疑会出现性能问题，可能造成卡顿甚至 ANR。

## 总结

撸完 SharedPreferences 源码，槽点可真不少！

1. 不支持跨进程，`MODE_MULTI_PROCESS` 也没用。跨进程频繁读写可能导致数据损坏或丢失。
2. 初始化的时候会读取 sp 文件，可能导致后续 `getXXX()` 方法阻塞。建议提前异步初始化 SharedPreferences。
3. sp 文件的数据会全部保存在内存中，所以不宜存放大数据。
4. `edit()` 方法每次都会新建一个 `EditorImpl` 对象。建议一次 edit()，多次 putXXX() 。
5. 无论是 `commit()` 还是 `apply()` ，针对任何修改都是全量写入。建议针对高频修改的配置项存在子啊单独的 sp 文件。
6. `commit()` 同步保存，有返回值。`apply()` 异步保存，无返回值。按需取用。
7. `onPause()` 、`onReceive()` 等时机会等待异步写操作执行完成，可能造成卡顿或者 ANR。

这么多问题，我们是不是不应该使用 SharedPreferences 呢？答案肯定不是的。如果你不需要跨进程，仅仅存储少量的配置项，SharedPreferences 仍然是一个很好的选择。

如果 SharedPreferences 已经满足不了你的需求了，给你推荐 Tencent 开源的 [MMKV](https://github.com/Tencent/MMKV) !

> 文章首发微信公众号： **`秉心说`** ， 专注 Java 、 Android 原创知识分享，LeetCode 题解。
>
> 更多最新原创文章，扫码关注我吧！

![](https://user-gold-cdn.xitu.io/2019/4/27/16a5f352eab602c4?w=2800&h=800&f=jpeg&s=178470)