let ws, loggedInUser, sessionToken;
let kicked = false;

ws = new WebSocket("ws://" + location.hostname + ":8081/wschat");

ws.onopen = function () {
    console.log("WebSocket connection established.");
};

ws.onmessage = function (event) {
    const m = JSON.parse(event.data);
    switch (m.type) {
        case "login_response":
            if (m.success) {
                loggedInUser = m.user;
                sessionToken = m.token;

                document.getElementById("authenticateArea").style.display = "none";
                document.getElementById("chatArea").style.display = "";
                document.getElementById("body").focus();

                ws.send(JSON.stringify({
                    type: "hello",
                    user: loggedInUser,
                    token: sessionToken
                }));

                alert("Login successful! Welcome, " + loggedInUser + ".");
            } else {
                alert("Login failed: " + m.message);
            }
            break;
        case "register_response":
            if (m.success) {
                alert(m.message || "Registration successful! You can now log in.");
                var loginTab = new bootstrap.Tab(document.getElementById("login-tab"));
                loginTab.show();
                document.getElementById("registerForm").reset();
            } else {
                alert("Registration failed: " + m.message);
            }
            break;
        case "roster": {
            const ul = document.getElementById("userList");
            ul.innerHTML = "";
            (m.users || []).forEach(u => {
                const li = document.createElement("li");
                li.textContent = u;
                li.className = "list-group-item";
                ul.appendChild(li);
            });
            break;
        }
        case "text": {
            const log = document.getElementById("chatLog");
            const div = document.createElement("div");
            div.textContent = `${m.user}: ${m.body}`;
            log.appendChild(div);
            log.scrollTop = log.scrollHeight;
            break;
        }
        case "kick": {
            const log = document.getElementById("chatLog");
            const div = document.createElement("div");
            div.textContent = m.body;
            div.className = "text-danger fw-bold";
            log.appendChild(div);
            kicked = true;
            if (ws) ws.close();
            break;
        }
        case "server_shutdown": {
            const log = document.getElementById("chatLog");
            const div = document.createElement("div");
            div.textContent = "The server is shutting down. Please refresh the page.";
            div.className = "text-danger fw-bold";
            log.appendChild(div);
            kicked = true;
            if (ws) ws.close();
            break;
        }
    }
};

ws.onclose = function () {
    document.getElementById("authenticateArea").style.display = "";
    document.getElementById("enterChatForm").style.display = "none";
    document.getElementById("chatArea").style.display = "none";
    document.getElementById("userList").innerHTML = "";
    if (!kicked) {
        document.getElementById("chatLog").innerHTML = "";
    }
    loggedInUser = null;
    sessionToken = null;
    kicked = false;
    ws = null;
};

ws.onerror = function (error) {
    console.error("WebSocket error:", error);
    alert("An error occurred with the WebSocket connection. Please try refreshing the page.");
    document.getElementById("authenticateArea").style.display = "";
    document.getElementById("enterChatForm").style.display = "none";
    document.getElementById("chatArea").style.display = "none";
    ws = null;
};

async function ensureWebSocketConnected() {
    if (!ws) {
        return Promise.reject(new Error("WebSocket is not initialised, please refresh the page."));
    }

    if (ws.readyState === WebSocket.OPEN) {
        return Promise.resolve();
    }

    if (ws.readyState === WebSocket.CONNECTING) {
        return new Promise((resolve, reject) => {
            const tempOnOpen = () => {
                cleanup();
                resolve();
            };

            const tempOnError = (error) => {
                cleanup();
                reject(new Error("WebSocket connection failed: " + error));
            };

            const cleanup = () => {
                ws.removeEventListener("open", tempOnOpen);
                ws.removeEventListener("error", tempOnError);
            };

            ws.addEventListener("open", tempOnOpen);
            ws.addEventListener("error", tempOnError);

            setTimeout(() => {
                cleanup();
                reject(new Error("WebSocket connection timed out."));
            }, 10000);
        });
    }
    return Promise.reject(new Error(`WebSocket is not open (state: ${ws.readyState}), please refresh the page.`));
}

async function loginUser() {
    try {
        await ensureWebSocketConnected();
        const username = document.getElementById("loginUser").value;
        const password = document.getElementById("loginPassword").value;
        if (!username || !password) {
            alert("Please enter both username and password.");
            return;
        }
        const token = document.getElementById("loginToken").value.trim();

        const loginMessage = {
            type: "login",
            user: username,
            password: password,
            token: token
        };
        ws.send(JSON.stringify(loginMessage));
    } catch (error) {
        console.error("Error during login:", error);
        alert("An error occurred during login. Please try again.");
    }
}

async function registerUser() {
    try {
        await ensureWebSocketConnected();
        const username = document.getElementById("registerUser").value;
        const password = document.getElementById("registerPassword").value;
        if (!username || !password) {
            alert("Please enter both username and password.");
            return;
        }
        const registerMessage = {
            type: "register",
            user: username,
            password: password
        };
        ws.send(JSON.stringify(registerMessage));
    } catch (error) {
        console.error("Error during registration:", error);
        alert("An error occurred during registration. Please try again.");
    }
}

window.addEventListener("DOMContentLoaded", () => {
    document.getElementById("loginForm")
        .addEventListener("submit", e => {
            e.preventDefault();
            loginUser();
        });
    document.getElementById("registerForm")
        .addEventListener("submit", e => {
            e.preventDefault();
            registerUser();
        });
});

function sendMsg() {
    let body = document.getElementById("body").value;
    if (!body || !ws || ws.readyState !== WebSocket.OPEN || !loggedInUser) return;
    ws.send(JSON.stringify({ type: "text", user: loggedInUser, body: body }));
    document.getElementById("body").value = "";
}

window.onload = function () {
    console.log("Chat page loaded. Initializing WebSocket connection...");
};