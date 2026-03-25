package com.mentorplatform.mentor_platform.dto;

/**
 * STOMP payload for WebRTC signaling messages.
 *
 * Three message types are exchanged during WebRTC negotiation:
 *
 * 1. SDP OFFER — sent by the caller (mentor) to start negotiation
 * {
 *   "type":      "OFFER",
 *   "sessionId": "abc-123",
 *   "senderId":  "mentor-uuid",
 *   "targetId":  "student-uuid",
 *   "sdp":       "v=0\r\no=- 46117... (SDP string)"
 * }
 *
 * 2. SDP ANSWER — sent by the callee (student) in response
 * {
 *   "type":      "ANSWER",
 *   "sessionId": "abc-123",
 *   "senderId":  "student-uuid",
 *   "targetId":  "mentor-uuid",
 *   "sdp":       "v=0\r\no=- 12345... (SDP string)"
 * }
 *
 * 3. ICE CANDIDATE — sent by both sides during connectivity checks
 * {
 *   "type":      "ICE_CANDIDATE",
 *   "sessionId": "abc-123",
 *   "senderId":  "mentor-uuid",
 *   "targetId":  "student-uuid",
 *   "candidate": "candidate:1 1 UDP 2113937151 192.168.1.1 54400 ...",
 *   "sdpMid":    "0",
 *   "sdpMLineIndex": 0
 * }
 *
 * The Spring backend is a pure relay — it never inspects
 * the SDP or ICE content, just routes to the right topic.
 */
public record SignalMessage(

        /** OFFER | ANSWER | ICE_CANDIDATE */
        String type,

        /** UUID of the session both peers are in */
        String sessionId,

        /** UUID of the user sending this signal */
        String senderId,

        /** UUID of the user this signal is intended for */
        String targetId,

        /** SDP string — present for OFFER and ANSWER, null for ICE_CANDIDATE */
        String sdp,

        /** ICE candidate string — present for ICE_CANDIDATE, null for SDP messages */
        String candidate,

        /** SDP media line id — present for ICE_CANDIDATE */
        String sdpMid,

        /** SDP media line index — present for ICE_CANDIDATE */
        Integer sdpMLineIndex
) {}