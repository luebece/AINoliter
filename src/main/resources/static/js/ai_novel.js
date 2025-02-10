var stompClient = null;
var countdownInterval = null;
var countdownValue = 0;

function connect() {
    var socket = new SockJS('/chat-websocket');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);

        // 스토리 업데이트 구독
        stompClient.subscribe('/user/queue/novel-updates', function (message) { // 구독 경로 변경
            var storyUpdate = JSON.parse(message.body);
            updateStory(storyUpdate);
        });
    });
}


function selectTopic(topic) {
    // initialPrompt 생성 (클라이언트 측에서!)
    const initialPrompt = `당신은 창의적인 소설 작가입니다. 장르는 ${topic}입니다.이 장르를 바탕으로 흥미진진한 소설의 시작 부분을 작성하세요. 등장인물과 배경 묘사를 포함하고, 독창적이고 창의적인 스토리를 만들어주세요. 이야기의 첫 부분만 작성해주세요. 소설의 톤은 ${topic} 장르에 맞게 작성해주세요. 주의: 주제의 정의, 설명, 또는 특정 작품에 대한 정보는 제공하지 마세요. 오직 소설의 시작 부분만 작성해주세요. 만약 주제의 정의, 설명, 또는 특정 작품에 대한 정보를 제공하면, 이는 잘못된 응답입니다. 반드시 소설의 시작 부분만 작성하세요.`;

    const message = {
        text: initialPrompt, // initialPrompt를 text로 전송
        isTopicSelection: true
    };
    stompClient.send("/app/novel-input", {}, JSON.stringify(message));
    startLoading();
}

function sendUserInput() {
    var userInput = document.getElementById('user-input').value;
    if (userInput.trim() !== "") {
        const message = {
            text: userInput,
            isTopicSelection: false
        };
        stompClient.send("/app/novel-input", {}, JSON.stringify(message));
        document.getElementById('user-input').value = '';
        showMessage({ text: userInput, isUserMessage: true }, true);
        startLoading();
    }
}

function updateStory(storyUpdate) {
    stopLoading();
    showMessage(storyUpdate, false);
}

function showMessage(message, isUserMessage) {
    var storyOutput = document.getElementById('story-output');
    var messageElement = document.createElement('div');
    messageElement.classList.add(isUserMessage ? 'user-message' : 'gemini-message');
    messageElement.textContent = message.text; // 초기 프롬프트 및 Gemini 응답이 여기에 표시됨
    storyOutput.appendChild(messageElement);
    storyOutput.scrollTop = storyOutput.scrollHeight;
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

document.addEventListener('DOMContentLoaded', function () {
    connect();

    // 주제 선택 버튼 이벤트
    document.querySelectorAll('.topic-button').forEach(function (button) {
        button.addEventListener('click', function () {
            var topic = this.getAttribute('data-topic');
            selectTopic(topic);
        });
    });

    // 사용자 입력 이벤트
    document.getElementById('send-button').addEventListener('click', sendUserInput);
    document.getElementById('user-input').addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            sendUserInput();
        }
    });
});