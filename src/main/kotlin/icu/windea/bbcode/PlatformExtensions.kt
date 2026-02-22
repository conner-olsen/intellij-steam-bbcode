@file:Suppress("unused", "NOTHING_TO_INLINE")

package icu.windea.bbcode

import com.google.common.util.concurrent.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.*
import com.intellij.psi.*
import com.intellij.psi.util.*
import com.intellij.util.*
import icu.windea.bbcode.util.*
import java.util.concurrent.*
import kotlin.reflect.*

inline fun <T> cancelable(block: () -> T): T {
    try {
        return block()
    } catch (e: ExecutionException) {
        val cause = e.cause
        if (cause is ProcessCanceledException) throw cause
        throw cause ?: e
    } catch (e: UncheckedExecutionException) {
        val cause = e.cause
        if (cause is ProcessCanceledException) throw cause
        throw cause ?: e
    } catch (e: ProcessCanceledException) {
        throw e
    }
}

inline fun <T> cancelable(defaultValueOnException: (Throwable) -> T, block: () -> T): T {
    try {
        return block()
    } catch (e: ExecutionException) {
        val cause = e.cause
        if (cause is ProcessCanceledException) throw cause
        return defaultValueOnException(cause ?: e)
    } catch (e: UncheckedExecutionException) {
        val cause = e.cause
        if (cause is ProcessCanceledException) throw cause
        return defaultValueOnException(cause ?: e)
    } catch (e: ProcessCanceledException) {
        throw e
    }
}

inline fun <R> runCatchingCancelable(block: () -> R): Result<R> {
    return runCatching(block).onFailure { if (it is ProcessCanceledException) throw it }
}

inline fun <T, R> T.runCatchingCancelable(block: T.() -> R): Result<R> {
    return runCatching(block).onFailure { if (it is ProcessCanceledException) throw it }
}


inline fun <T> UserDataHolder.tryPutUserData(key: Key<T>, value: T?) {
    runCatchingCancelable { putUserData(key, value) }
}

inline fun <T> UserDataHolder.getOrPutUserData(key: Key<T>, action: () -> T): T {
    val data = this.getUserData(key)
    if (data != null) return data
    val newValue = action()
    if (newValue != null) putUserData(key, newValue)
    return newValue
}

inline fun <T> UserDataHolder.getOrPutUserData(key: Key<T>, nullValue: T, action: () -> T?): T? {
    val data = this.getUserData(key)
    if (data != null) return data.takeUnless { it == nullValue }
    val newValue = action()
    if (newValue != null) putUserData(key, newValue) else putUserData(key, nullValue)
    return newValue
}

fun <T, THIS : UserDataHolder> THIS.getUserDataOrDefault(key: Key<T>): T? {
    val value = this.getUserData(key)
    return when {
        value != null -> value
        key is KeyWithDefaultValue -> key.defaultValue.also { putUserData(key, it) }
        key is KeyWithFactory<*, *> -> {
            val key0 = key.cast<KeyWithFactory<T, THIS>>()
            key0.factory(this).also { putUserData(key0, it) }
        }

        else -> null
    }
}

fun <T> ProcessingContext.getOrDefault(key: Key<T>): T? {
    val value = this.get(key)
    return when {
        value != null -> value
        key is KeyWithDefaultValue -> key.defaultValue.also { put(key, it) }
        else -> null
    }
}

inline operator fun <T> Key<T>.getValue(thisRef: UserDataHolder, property: KProperty<*>): T? {
    return thisRef.getUserDataOrDefault(this)
}

inline operator fun <T> Key<T>.getValue(thisRef: ProcessingContext, property: KProperty<*>): T? {
    return thisRef.getOrDefault(this)
}

inline operator fun <T, THIS : UserDataHolder> KeyWithFactory<T, THIS>.getValue(thisRef: THIS, property: KProperty<*>): T {
    return thisRef.getUserData(this) ?: factory(thisRef).also { thisRef.putUserData(this, it) }
}

inline operator fun <T> KeyWithFactory<T, ProcessingContext>.getValue(thisRef: ProcessingContext, property: KProperty<*>): T {
    return thisRef.get(this) ?: factory(thisRef).also { thisRef.put(this, it) }
}

inline operator fun <T> Key<T>.setValue(thisRef: UserDataHolder, property: KProperty<*>, value: T?) {
    thisRef.putUserData(this, value)
}

inline operator fun <T> Key<T>.setValue(thisRef: ProcessingContext, property: KProperty<*>, value: T?) {
    thisRef.put(this, value)
}

inline operator fun <T> DataKey<T>.getValue(thisRef: DataContext, property: KProperty<*>): T? {
    return thisRef.getData(this)
}

inline operator fun <T> DataKey<T>.getValue(thisRef: AnActionEvent, property: KProperty<*>): T? {
    return thisRef.dataContext.getData(this)
}

//compatible with test cases
private var defaultProject0: Project? = null
var defaultProject: Project
    get() = run { defaultProject0 ?: ProjectManager.getInstance().defaultProject }
    set(value) = run { defaultProject0 = value }

private object EmptyPointer : SmartPsiElementPointer<PsiElement> {
    override fun getElement() = null

    override fun getContainingFile() = null

    override fun getProject() = defaultProject

    override fun getVirtualFile() = null

    override fun getRange() = null

    override fun getPsiRange() = null
}

fun <T : PsiElement> emptyPointer(): SmartPsiElementPointer<T> = EmptyPointer.cast()

fun SmartPsiElementPointer<*>.isEmpty() = this === EmptyPointer

fun <E : PsiElement> E.createPointer(project: Project = this.project): SmartPsiElementPointer<E> {
    return try {
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this)
    } catch (e: IllegalArgumentException) {
        //Element from alien project - use empty pointer
        emptyPointer()
    }
}

fun <E : PsiElement> E.createPointer(file: PsiFile?, project: Project = this.project): SmartPsiElementPointer<E> {
    return try {
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this, file)
    } catch (e: IllegalArgumentException) {
        //Element from alien project - use empty pointer
        emptyPointer()
    }
}

/** Convert a `VirtualFile` to a `PsiFile`. */
inline fun VirtualFile.toPsiFile(project: Project): PsiFile? {
    return PsiManager.getInstance(project).findFile(this)
}

/** Convert a `VirtualFile` to a `PsiDirectory`. */
inline fun VirtualFile.toPsiDirectory(project: Project): PsiDirectory? {
    return PsiManager.getInstance(project).findDirectory(this)
}

/** Convert a `VirtualFile` to a `PsiFile` or `PsiDirectory`. */
inline fun VirtualFile.toPsiFileSystemItem(project: Project): PsiFileSystemItem? {
    return if (this.isFile) PsiManager.getInstance(project).findFile(this) else PsiManager.getInstance(project).findDirectory(this)
}

fun PsiElement.children(forward: Boolean = true): Sequence<PsiElement> {
    return if(forward) this.firstChild?.siblings(forward = true).orEmpty() else this.lastChild?.siblings(forward = false).orEmpty()
}
