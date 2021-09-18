package ru.netology.nmedia.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.lifecycle.*
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.netology.nmedia.R
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.dto.*
import ru.netology.nmedia.model.FeedModelState
import ru.netology.nmedia.model.PhotoModel
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.util.SingleLiveEvent
import javax.inject.Inject
import kotlin.random.Random

private val empty = Post(
    id = 0,
    content = "",
    authorId = 0,
    author = "",
    authorAvatar = "",
    likedByMe = false,
    likes = 0,
    published = 0,
)

private val noPhoto = PhotoModel()
private const val today = 24* 60* 60;
private const val yesterday = 2* 24* 60* 60;

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PostViewModel @Inject constructor(
    private val repository: PostRepository,
    auth: AppAuth,
    application: Application,
) : AndroidViewModel(application) {
    private val cached: Flow<PagingData<FeedItem>> = repository
        .data
        .map { pagingData ->
            var todayInserted = false
            var yesterdayInserted = false
            var othersInserted = false
            pagingData.insertSeparators(
                terminalSeparatorType = TerminalSeparatorType.SOURCE_COMPLETE,
                generator = { before, after ->
                    val curTime = System.currentTimeMillis() / 1000;
                    if (!todayInserted && after?.published != null && (curTime - after.published <= today)) {
                        todayInserted = true;
                        PostTime(
                            Random.nextLong(),
                            timeString = getApplication<Application>().getString(R.string.today),
                        )
                    } else if (!yesterdayInserted && after?.published != null && (curTime - after.published > today && curTime - after.published <= yesterday )) {
                        yesterdayInserted = true
                        PostTime(
                            Random.nextLong(),
                            timeString = getApplication<Application>().getString(R.string.yesterday),
                        )
                    } else if (!othersInserted && after?.published != null && (curTime - after.published > yesterday)) {
                        othersInserted = true
                        PostTime(
                            Random.nextLong(),
                            timeString = getApplication<Application>().getString(R.string.week_ago),
                        )
                    } else if (before?.id?.rem(5) != 0L) null else
                        Ad(
                            Random.nextLong(),
                            "https://netology.ru",
                            "figma.jpg"
                        )
                }
            )

        }
        .cachedIn(viewModelScope)

    val data: Flow<PagingData<FeedItem>> = auth.authStateFlow
        .flatMapLatest { (myId, _) ->
            cached
                .map { pagingData ->
                    pagingData.map { item ->
                        if (item !is Post) item else item.copy(ownedByMe = item.authorId == myId)
                    }
                }
        }

    private val _dataState = MutableLiveData<FeedModelState>()
    val dataState: LiveData<FeedModelState>
        get() = _dataState

    private val edited = MutableLiveData(empty)
    private val _postCreated = SingleLiveEvent<Unit>()
    val postCreated: LiveData<Unit>
        get() = _postCreated

    private val _photo = MutableLiveData(noPhoto)
    val photo: LiveData<PhotoModel>
        get() = _photo

    init {
        loadPosts()
    }

    fun loadPosts() = viewModelScope.launch {
        try {
            _dataState.value = FeedModelState(loading = true)
            // repository.stream.cachedIn(viewModelScope).
            _dataState.value = FeedModelState()
        } catch (e: Exception) {
            _dataState.value = FeedModelState(error = true)
        }
    }

    fun refreshPosts() = viewModelScope.launch {
        try {
            _dataState.value = FeedModelState(refreshing = true)
//            repository.getAll()
            _dataState.value = FeedModelState()
        } catch (e: Exception) {
            _dataState.value = FeedModelState(error = true)
        }
    }

    fun save() {
        edited.value?.let {
            viewModelScope.launch {
                try {
                    repository.save(
                        it, _photo.value?.uri?.let { MediaUpload(it.toFile()) }
                    )

                    _postCreated.value = Unit
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        edited.value = empty
        _photo.value = noPhoto
    }

    fun edit(post: Post) {
        edited.value = post
    }

    fun changeContent(content: String) {
        val text = content.trim()
        if (edited.value?.content == text) {
            return
        }
        edited.value = edited.value?.copy(content = text)
    }

    fun changePhoto(uri: Uri?) {
        _photo.value = PhotoModel(uri)
    }

    fun likeById(id: Long) {
        TODO()
    }

    fun removeById(id: Long) {
        TODO()
    }
}
