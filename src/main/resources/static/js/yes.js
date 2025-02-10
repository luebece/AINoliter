var stompClient = null;
let startTime = null;
var choiceCount = 0;

function connect() {
    var socket = new SockJS('/chat-websocket');
    stompClient = Stomp.over(socket);

    // 세션에서 accessToken 가져오기
    var accessToken = sessionStorage.getItem("accessToken");

    if (accessToken) {
        stompClient.connect({
            'Authorization': `Bearer ${accessToken}`
        }, function (frame) {
            console.log('Connected: ' + frame);
            isWebSocketConnected = true;
            stompClient.subscribe('/user/queue/game', function (message) { // 구독 경로 변경
                console.log("Received message:", message.body);
                var parsedMessage = JSON.parse(message.body);
                if (parsedMessage.geminiResponse) {
                    if (typeof parsedMessage.geminiResponse === 'object') {
                        handleGeminiResponse(parsedMessage.geminiResponse.geminiResponse, parsedMessage.geminiResponse.summarizedPrompt);
                    } else {
                        handleGeminiResponse(parsedMessage.geminiResponse, null);
                    }
                }
            });
            stompClient.subscribe('/user/queue/errors', function (error) { // 구독 경로 변경
                console.error("Error received:", error.body);
                showError(error.body);
            });
        }, function (error) {
            console.error("STOMP connection error:", error);
            setTimeout(connect, 5000); // 연결 실패 시 재시도
        });
    } else {
        console.error("accessToken is null. WebSocket connection failed.");
    }
}


// 새로고침 시 서버 상태 초기화 (CSRF 토큰 사용 X)
window.addEventListener('beforeunload', function () {
    // /game/reset 호출 (로그인되어 있을 때만)
    if (sessionStorage.getItem("accessToken")) {
        fetch('/game/reset', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
        }).then(response => {
            if (response.ok) {
                console.log('Game state reset successfully.');
            } else {
                console.error('Failed to reset game state.');
            }
        }).catch(error => {
            console.error('Error resetting game state:', error);
        });
    } else {
        console.log("User is not authenticated. Skipping reset request.");
    }
});

async function generateImageWithImagen(prompt) {
    // 세션에서 accessToken 가져오기
    var accessToken = sessionStorage.getItem("accessToken");

    console.log("Token from accessToken variable in generateImageWithImagen:", accessToken);

    if (!accessToken) {
        console.error("Token is not set.");
        return null;
    }

    try {
        const response = await fetch(`/game/api/generate-image?prompt=${encodeURIComponent(prompt)}`, {
            method: "POST", // POST 요청으로 수정
            headers: {
                "Authorization": `Bearer ${accessToken}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ prompt: prompt }) // 요청 본문에 prompt 추가
        });

        if (!response.ok) {
            const errorData = await response.text();
            console.error("Imagen 3 API error:", errorData);
            throw new Error(`Imagen 3 API request failed with status ${response.status}`);
        }

        // JSON 응답에서 이미지 데이터 가져오기
        const data = await response.json();
        console.log("Imagen 3 API response data:", data);

        if (data && data.imageData) {
            const imageUrl = `data:image/png;base64,${data.imageData}`;
            console.log("Generated image URL:", imageUrl);
            return imageUrl;
        } else {
            console.error("No image data found in the Imagen 3 API response.");
            return null;
        }
    } catch (error) {
        console.error("Error calling Imagen 3 API:", error);
        return null;
    }
}

function displayImage(imageUrl) {
    const imageContainer = document.getElementById('image-container');
    console.log("Image container before loading image:", imageContainer);
    imageContainer.innerHTML = '';

    const imgElement = document.createElement('img');
    imgElement.src = imageUrl;
    imgElement.style.width = "90%";
    imgElement.style.height = "auto";
    imgElement.style.maxHeight = "600px";
    imgElement.style.objectFit = "cover";
    imgElement.style.margin = "0 auto";

    imgElement.onload = () => {
        console.log("Image loaded successfully.");
        let choiceContainer = document.getElementById('choice-container');
        console.log("Choice container after image load:", choiceContainer);

        if (!choiceContainer) {
            console.warn("Choice container not found after image load. Creating a new one...");
            choiceContainer = document.createElement('div');
            choiceContainer.id = 'choice-container';
            choiceContainer.style.display = 'flex';
            choiceContainer.style.flexDirection = 'column';
            choiceContainer.style.alignItems = 'center';
            document.body.appendChild(choiceContainer);
            console.log("New choice container created after image load:", choiceContainer);
        } else {
            console.log("Choice container found after image load:", choiceContainer);
            choiceContainer.style.display = 'flex';
        }
    };

    imageContainer.appendChild(imgElement);
}

async function handleGeminiResponse(geminiResponse, summarizedPrompt) {
    console.log("handleGeminiResponse called with:", geminiResponse, summarizedPrompt);

    let isQuotaExceeded = false;
    try {
        // 오류 메시지가 JSON 형식인 경우 파싱
        const errorResponse = JSON.parse(geminiResponse);
        if (errorResponse.error && errorResponse.error.code === 429) {
            isQuotaExceeded = true;
        }
    } catch (e) {
        // JSON 파싱 실패 시 문자열에서 "RESOURCE_EXHAUSTED" 확인
        if (geminiResponse.includes("RESOURCE_EXHAUSTED")) {
            isQuotaExceeded = true;
        }
    }

    if (isQuotaExceeded) {
        hideLoadingAnimation();
        alert("일시적인 오류가 났습니다 선택지를 다시 한번 더 눌러주세요");
        return;
    }

    // 이미지 생성 시도
    let imageUrl = null;
    if (summarizedPrompt) {
        try {
            imageUrl = await generateImageWithImagen(summarizedPrompt);
            if (imageUrl) {
                displayImage(imageUrl);
            } else {
                console.error("Imagen 3 API did not return a valid image URL.");
                showError("이미지 생성에 실패했습니다 잠시동안 텍스트로 즐겨주세요");
            }
        } catch (error) {
            console.error("Error generating image:", error);
            showError("이미지 생성 중 오류가 발생했습니다: " + error.message);
        }
    }

    // 이미지 생성이 완료된 후 Gemini 응답 표시
    showDialogMessage({ geminiResponse: geminiResponse }, false);

    // 선택지 버튼 업데이트
    let choiceContainer = document.getElementById('choice-container');
    console.log("Choice container before update:", choiceContainer);

    if (!choiceContainer) {
        console.warn("Choice container not found. Creating a new one...");
        choiceContainer = document.createElement('div');
        choiceContainer.id = 'choice-container';
        choiceContainer.style.display = 'flex';
        choiceContainer.style.flexDirection = 'column';
        choiceContainer.style.alignItems = 'center';
        document.body.appendChild(choiceContainer);
        console.log("New choice container created:", choiceContainer);
    }

    updateChoiceButtons(geminiResponse);

    hideLoadingAnimation();
}

// 선택지 버튼 업데이트 함수
function updateChoiceButtons(response) {
    console.log("Updating choice buttons with response:", response);

    // Gemini 응답에서 선택지 추출 (1., 2., 3. 등으로 시작하는 줄을 찾음)
    var choices = response.split('\n')
        .filter(line => line.match(/^\d+\.\s/))
        .map(line => line.replace(/^\d+\.\s*/, '').trim());

    console.log("Extracted choices:", choices);

    var choiceContainer = document.getElementById('choice-container');
    console.log("Choice container in updateChoiceButtons:", choiceContainer);

    if (!choiceContainer) {
        console.error("Error: 'choice-container' element not found.");
        return;
    }

    // 선택지 컨테이너 초기화
    choiceContainer.innerHTML = '';

    if (choices && choices.length > 0) {
        choices.forEach((choice, index) => {
            var choiceButton = document.createElement('button');
            choiceButton.textContent = choice;
            choiceButton.classList.add('choice-button');
            choiceButton.style.display = 'block';
            choiceButton.style.width = "80%";
            choiceButton.style.margin = "20px 0";
            choiceButton.style.padding = "10px";
            choiceButton.style.fontSize = "16px";
            choiceButton.style.borderRadius = "5px";
            choiceButton.style.backgroundColor = "white";
            choiceButton.style.color = "black";
            choiceButton.style.border = "none";
            choiceButton.style.fontFamily = "'Noto Sans KR', sans-serif";
            choiceButton.style.cursor = "pointer";

            // 버튼 클릭 이벤트 추가
            choiceButton.addEventListener('click', function () {
                sendChoice(choice);
            });

            // 버튼을 컨테이너에 추가
            choiceContainer.appendChild(choiceButton);
        });

        // 선택지 컨테이너 스타일 조정
        choiceContainer.style.width = "70%";
        choiceContainer.style.margin = "-100 auto";
        choiceContainer.style.display = 'flex';
        choiceContainer.style.flexDirection = 'column';
        choiceContainer.style.alignItems = 'center';
    } else {
        choiceContainer.style.display = 'none';
    }
}

function showLoadingAnimation() {
    const loadingContainer = document.getElementById('loading-container');
    loadingContainer.style.display = 'block';
}

function hideLoadingAnimation() {
    const loadingContainer = document.getElementById('loading-container');
    loadingContainer.style.display = 'none';
}

// 선택지 전송 함수
function sendChoice(choice) {
    console.log("Sending choice:", choice);
    showLoadingAnimation();
    var modelSelect = document.getElementById('model-select');
    var modelName = modelSelect.value;
    var accessToken = sessionStorage.getItem("accessToken");

    if (!accessToken) {
        console.error("Access token not found.");
        return;
    }

    stompClient.send("/app/game", {
        'Authorization': `Bearer ${accessToken}`
    }, JSON.stringify({
        'text': choice,
        'modelName': modelName
    }));

    showDialogMessage({ text: choice }, true);
}

// 대화창에 메시지 표시 함수
function showDialogMessage(message, isUserMessage) {
    var dialogContainer = document.getElementById('dialog-container');
    dialogContainer.innerHTML = '';

    var messageContainer = document.createElement('div');
    messageContainer.classList.add('message-container');

    var messageElement = document.createElement('div');
    messageElement.classList.add(isUserMessage ? 'user-message' : 'gemini-message');
    messageElement.textContent = isUserMessage ? message.text : message.geminiResponse;

    messageElement.style.textAlign = "left";

    messageContainer.appendChild(messageElement);

    dialogContainer.appendChild(messageContainer);

    addToLog(message, isUserMessage);

    // 전체 대화 기록에 추가
    function addToLog(message, isUserMessage) {
        const logMessages = document.getElementById('log-messages');
        const logEntry = document.createElement('div');
        logEntry.classList.add('log-entry');

        // 라이트 노벨 스타일 텍스트 포맷팅
        const formattedText = isUserMessage
            ? `💬 선택지: ${message.text}`
            : `📖 ${message.geminiResponse.replace(/\n/g, '<br>')}`;

        logEntry.innerHTML = formattedText;
        logMessages.appendChild(logEntry);

        // 스크롤 자동 이동
        logMessages.scrollTop = logMessages.scrollHeight;
    }
}

// 에러 메시지 표시 함수
function showError(errorMessage) {
    var dialogContainer = document.getElementById('dialog-container');
    var errorMessageElement = document.createElement('div');
    errorMessageElement.classList.add('error-message');
    errorMessageElement.textContent = errorMessage;
    dialogContainer.appendChild(errorMessageElement);

    dialogContainer.scrollTop = dialogContainer.scrollHeight;
}
var isWebSocketConnected = false;

// DOM 로드 시
document.addEventListener('DOMContentLoaded', function () {
    document.getElementById('set-max-choices-btn').addEventListener('click', setMaxChoices);
    var startGameButton = document.getElementById('start-game');
    // 햄버거 버튼 클릭 이벤트
    document.getElementById('hamburger-button').addEventListener('click', function() {
        var sidePanel = document.getElementById('side-panel');
        var hamburgerButton = document.getElementById('hamburger-button');
        sidePanel.classList.toggle('open');
        hamburgerButton.classList.toggle('open');
    });

    // 모달 표시 이벤트
    document.getElementById('show-log-button').addEventListener('click', function() {
        document.getElementById('log-modal').style.display = 'block';
    });

    // 모달 닫기 이벤트
    document.querySelector('.close-modal').addEventListener('click', function() {
        document.getElementById('log-modal').style.display = 'none';
    });

    // 배경 클릭 시 모달 닫기
    window.addEventListener('click', function(event) {
        const modal = document.getElementById('log-modal');
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });

    // 게임 시작 버튼 이벤트
    startGameButton.addEventListener('click', function () {
        if (!isWebSocketConnected) {
            alert("로딩 중입니다. 잠시 후 다시 시도해주세요.");
            return;
        }


        var accessToken = sessionStorage.getItem("accessToken");
        if (accessToken) {
            document.getElementById('start-game').style.display = 'none';
            document.getElementById('name-input-container').style.display = 'block';

            // 게임 시작 메시지 전송 (WebSocket 연결은 이미 맺어진 상태)
            stompClient.send("/app/game", {
                'Authorization': `Bearer ${accessToken}`
            }, JSON.stringify({
                'text': 'start',
                'modelName': document.getElementById('model-select').value
            }));
        } else {
            alert("로그인이 필요합니다.");
        }
    });

    // 이름 입력 확인 버튼 이벤트
    document.getElementById('name-submit').addEventListener('click', function () {
        var accessToken = sessionStorage.getItem("accessToken");
        var nameInput = document.getElementById('name-input');
        var name = nameInput.value;
        if (name.trim() !== "" && accessToken) {
            stompClient.send("/app/game", {
                'Authorization': `Bearer ${accessToken}`
            }, JSON.stringify({
                'text': name,
                'modelName': document.getElementById('model-select').value
            }));
            document.getElementById('name-input-container').style.display = 'none';
            document.getElementById('choice-container').style.display = 'flex';
        } else {
            alert("이름을 입력하고 로그인이 필요합니다.");
        }
    });
});