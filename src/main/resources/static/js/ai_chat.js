var stompClient = null;
var chatStarted = false; // 대화 시작 여부 변수 추가

function connect() {
    var socket = new SockJS('/chat-websocket');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);

        // AI 대화 메시지 구독
        stompClient.subscribe('/user/queue/ai-messages', function (message) {
            var parsedMessage = JSON.parse(message.body);
            showMessage(parsedMessage);
        });

        // 성격 설정 업데이트 구독
        stompClient.subscribe('/user/queue/personality-updates', function (update) {
            alert(update.body);
        });
    });
}

function startChat() {
    chatStarted = true; // 대화 시작 상태로 변경
    clearChatOutput(); // 대화 내용 초기화
    stompClient.send("/app/start-ai-chat", {}, JSON.stringify({}));
    // 모델 1 로딩 상태 표시
    showLoading("모델 1");
}

function continueChat() {
    if (chatStarted) {
        stompClient.send("/app/continue-ai-chat", {}, JSON.stringify({}));
        // 모델 1 로딩 상태 표시 (모델 1이 먼저 응답)
        showLoading("모델 1");
    } else {
        alert("대화를 먼저 시작하세요.");
    }
}

function setPersonality(modelName, personality) {
    stompClient.send("/app/set-personality", {}, JSON.stringify({
        'modelName': modelName,
        'personality': personality
    }));
}

// 로딩 상태와 타이머를 표시하는 함수
function showLoading(modelName) {
    var chatOutput = document.getElementById('chat-output');
    var loadingBubble = createLoadingBubble(modelName);
    chatOutput.appendChild(loadingBubble);
    chatOutput.scrollTop = chatOutput.scrollHeight;

    var startTime = new Date().getTime();
    var timerInterval = setInterval(function() {
        var elapsedTime = Math.round((new Date().getTime() - startTime) / 1000);
        updateLoadingTime(loadingBubble, elapsedTime);
    }, 1000);

    loadingStates[modelName] = {
        interval: timerInterval,
        bubble: loadingBubble
    };
}

// 로딩 말풍선 생성 함수
function createLoadingBubble(modelName) {
    var bubble = document.createElement('div');
    bubble.classList.add('message-bubble', 'loading-message');

    var modelNameDiv = document.createElement('div');
    modelNameDiv.classList.add('model-name');
    modelNameDiv.textContent = modelName + ":";
    bubble.appendChild(modelNameDiv);

    var loadingContainer = document.createElement('div');
    loadingContainer.classList.add('loading-container');

    var loadingDots = document.createElement('div');
    loadingDots.classList.add('loading-dots');
    for (var i = 0; i < 3; i++) {
        var dot = document.createElement('span');
        dot.classList.add('dot');
        loadingDots.appendChild(dot);
    }
    loadingContainer.appendChild(loadingDots);

    var loadingTime = document.createElement('span');
    loadingTime.classList.add('loading-time');
    loadingTime.textContent = "0초";
    loadingContainer.appendChild(loadingTime);

    bubble.appendChild(loadingContainer);
    return bubble;
}

// 로딩 시간을 업데이트하는 함수
function updateLoadingTime(bubble, elapsedTime) {
    var loadingTimeElement = bubble.querySelector('.loading-time');
    if (loadingTimeElement) {
        loadingTimeElement.textContent = elapsedTime + "초";
    }
}

// 로딩 상태와 타이머를 제거하는 함수
function hideLoading(modelName) {
    var loadingState = loadingStates[modelName];
    if (loadingState) {
        clearInterval(loadingState.interval);
        loadingState.bubble.remove();
        delete loadingStates[modelName];
    }
}

function showMessage(message) {
    var chatOutput = document.getElementById('chat-output');

    // 모델 1 응답 처리
    if (message.model1Response) {
        hideLoading("모델 1");
        var model1Bubble = createMessageBubble("모델 1", message.model1Response, "gemini1-message");
        chatOutput.appendChild(model1Bubble);
    }

    // 모델 2 응답 처리
    if (message.model2Response) {
        hideLoading("모델 2");
        var model2Bubble = createMessageBubble("모델 2", message.model2Response, "gemini2-message");
        chatOutput.appendChild(model2Bubble);
    }

    // 모든 메시지를 추가한 후 스크롤을 맨 아래로 이동
    chatOutput.scrollTop = chatOutput.scrollHeight;

    // 모델 2 로딩 상태 표시는 대화 시작 또는 계속 시에만
    if (chatStarted && !message.model2Response) {
        showLoading("모델 2");
    }
}

function clearChatOutput() {
    var chatOutput = document.getElementById('chat-output');
    chatOutput.innerHTML = '';
}

// 로딩 상태 관리를 위한 객체
var loadingStates = {};

function createMessageBubble(modelName, messageText, className) {
    var bubble = document.createElement('div');
    bubble.classList.add('message-bubble', className);

    var modelNameDiv = document.createElement('div');
    modelNameDiv.classList.add('model-name');
    modelNameDiv.textContent = modelName + ":";
    bubble.appendChild(modelNameDiv);

    var messageTextDiv = document.createElement('div');
    messageTextDiv.textContent = messageText;
    bubble.appendChild(messageTextDiv);

    return bubble;
}

document.addEventListener('DOMContentLoaded', function () {
    connect();

    // 대화 시작
    document.getElementById('start-chat-button').addEventListener('click', startChat);

    // 대화 계속
    document.getElementById('continue-chat-button').addEventListener('click', continueChat);

    // 모델 1 성격 설정
    document.getElementById('set-model1-personality').addEventListener('click', function () {
        var personality = document.getElementById('model1-personality').value;
        setPersonality("model1", personality);
    });

    // 모델 2 성격 설정
    document.getElementById('set-model2-personality').addEventListener('click', function () {
        var personality = document.getElementById('model2-personality').value;
        setPersonality("model2", personality);
    });

    function setPersonality(modelId, personality) {
        stompClient.send("/app/set-personality", {}, JSON.stringify({
            'modelId': modelId,
            'personality': personality
        }));
    }
});