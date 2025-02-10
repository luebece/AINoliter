var stompClient = null;
var countdownInterval = null;
var countdownValue = 0;
var currentModelName = ""; // 현재 선택된 모델 이름

function connect() {
    var socket = new SockJS('/chat-websocket');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/user/queue/messages', function (message) { // 구독 경로 변경
            var parsedMessage = JSON.parse(message.body);
            if (parsedMessage.text || parsedMessage.geminiResponse) { // 빈 메시지가 아닌 경우만 처리
                stopLoading(); // 로딩 중지
                showMessage(parsedMessage, false); // Gemini 메시지
            }
        });
        stompClient.subscribe('/user/queue/errors', function (error) { // 구독 경로 변경
            stopLoading(); // 로딩 중지
            showError(error.body); // 에러 메시지
        });
    });
}


function sendMessage() {
    var messageInput = document.getElementById('message-input');
    var modelSelect = document.getElementById('model-select');
    var message = messageInput.value;
    var modelName = modelSelect.value; // 선택한 모델 이름

    if (message.trim() !== "") {
        stompClient.send("/app/chat", {}, JSON.stringify({
            'text': message,
            'modelName': modelName // 모델 이름 전송
        }));
        messageInput.value = '';
        showMessage({ text: message }, true); // 사용자 메시지
        startLoading(); // 로딩 시작
    }
}

function showMessage(message, isUserMessage) {
    var chatOutput = document.getElementById('chat-output');
    var messageContainer = document.createElement('div');
    messageContainer.classList.add('message-container');

    var messageElement = document.createElement('div');
    // 사용자의 메시지인 경우 user-message 클래스 추가,
    // Gemini의 응답인 경우 gemini-message 클래스 추가
    messageElement.classList.add(isUserMessage ? 'user-message' : 'gemini-message');
    messageElement.textContent = isUserMessage ? message.text : message.geminiResponse;

    messageContainer.appendChild(messageElement);
    chatOutput.appendChild(messageContainer);

    // 스크롤을 최하단으로 이동
    chatOutput.scrollTop = chatOutput.scrollHeight;
}

function showError(errorMessage) {
    var chatOutput = document.getElementById('chat-output');
    var errorMessageElement = document.createElement('div');
    errorMessageElement.classList.add('error-message');
    errorMessageElement.textContent = errorMessage;
    chatOutput.appendChild(errorMessageElement);

    // 스크롤을 최하단으로 이동
    chatOutput.scrollTop = chatOutput.scrollHeight;
}

function startLoading() {
    document.getElementById('loading-container').style.display = 'flex';
    countdownValue = 0;
    updateCountdown();
    countdownInterval = setInterval(updateCountdown, 1000);
}

function stopLoading() {
    document.getElementById('loading-container').style.display = 'none';
    if (countdownInterval) {
        clearInterval(countdownInterval);
        countdownInterval = null;
    }
}

function updateCountdown() {
    countdownValue++;
    document.querySelector('.countdown').textContent = countdownValue + '초';
}

document.addEventListener('DOMContentLoaded', function() {
    connect();
    var sendButton = document.getElementById('send-button');
    if (sendButton) {
        sendButton.addEventListener('click', sendMessage);
    }

    var messageInput = document.getElementById('message-input');
    if (messageInput) {
        messageInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault(); // 기본 동작 방지
                sendMessage();
            }
        });
    }
});