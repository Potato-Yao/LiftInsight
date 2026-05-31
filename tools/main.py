import cv2
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from collections import deque
from scipy.signal import find_peaks, savgol_filter

# ---------------------------------------------------------------------------
# Backend selection: set USE_GPU=True to run YOLO-Pose on your NVIDIA GPU,
# or False to use the original MediaPipe CPU path.
# ---------------------------------------------------------------------------
# USE_GPU = True
USE_GPU = False

if USE_GPU:
    try:
        import torch
        from ultralytics import YOLO as _YOLO
        _has_cuda = torch.cuda.is_available()
        if _has_cuda:
            print(f"✅ YOLO-Pose backend — GPU: {torch.cuda.get_device_name(0)}")
        else:
            print("⚠️  CUDA not available, YOLO will run on CPU")
    except ImportError:
        print("⚠️  ultralytics not installed — falling back to MediaPipe CPU")
        USE_GPU = False

if not USE_GPU:
    import mediapipe as mp
    from mediapipe.tasks import python
    from mediapipe.tasks.python import vision

# Barbell detection settings
BARBELL_DETECTION_ENABLED = True
BARBELL_HISTORY_SIZE = 8  # Number of frames to average for smoothing

# --- Barbell physical constraints ---
# Maximum tilt from horizontal (degrees). A real barbell is nearly always < 15°.
BARBELL_MAX_TILT_DEG = 15.0
# Maximum jump between frames as a fraction of frame diagonal (prevents teleporting).
BARBELL_MAX_JUMP_FRAC = 0.08
# Grip-width / shoulder-width ratio bounds (a barbell grip is ~1.0–2.5× shoulder width).
BARBELL_GRIP_MIN_SHOULDER_RATIO = 0.6
BARBELL_GRIP_MAX_SHOULDER_RATIO = 3.0

# --- Body movement detection ---
# Minimum average angle change (degrees) between frames to consider the body as moving.
BODY_MOVE_ANGLE_THRESHOLD = 2.0

# Pose landmark connections (skeleton)
POSE_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7),  # Face
    (0, 4), (4, 5), (5, 6), (6, 8),  # Face
    (9, 10),  # Mouth
    (11, 12),  # Shoulders
    (11, 13), (13, 15),  # Left arm
    (12, 14), (14, 16),  # Right arm
    (15, 17), (15, 19), (15, 21), (17, 19),  # Left hand
    (16, 18), (16, 20), (16, 22), (18, 20),  # Right hand
    (11, 23), (12, 24), (23, 24),  # Torso
    (23, 25), (25, 27),  # Left leg
    (24, 26), (26, 28),  # Right leg
    (27, 29), (29, 31), (27, 31),  # Left foot
    (28, 30), (30, 32), (28, 32),  # Right foot
]

# Landmark indices for spine calculation
LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_HIP = 23
RIGHT_HIP = 24
LEFT_KNEE = 25
RIGHT_KNEE = 26
LEFT_ANKLE = 27
RIGHT_ANKLE = 28

# Landmark indices for barbell detection (wrists help locate barbell)
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_ELBOW = 13
RIGHT_ELBOW = 14

# ---------------------------------------------------------------------------
# COCO 17-keypoint ↔ MediaPipe 33-landmark mapping  (for YOLO-Pose backend)
# Landmarks without a COCO equivalent get visibility=0 and are auto-skipped
# by the drawing / angle code.  Foot-leg angle needs heel/toe (no COCO match)
# so it will gracefully return None when using YOLO.
# ---------------------------------------------------------------------------
COCO_TO_MEDIAPIPE = {
    0: 0,    # nose
    1: 2,    # left_eye
    2: 5,    # right_eye
    3: 7,    # left_ear
    4: 8,    # right_ear
    5: 11,   # left_shoulder
    6: 12,   # right_shoulder
    7: 13,   # left_elbow
    8: 14,   # right_elbow
    9: 15,   # left_wrist
    10: 16,  # right_wrist
    11: 23,  # left_hip
    12: 24,  # right_hip
    13: 25,  # left_knee
    14: 26,  # right_knee
    15: 27,  # left_ankle
    16: 28,  # right_ankle
}

YOLO_SKELETON = [
    (5, 6),             # shoulders
    (5, 7), (7, 9),     # left arm
    (6, 8), (8, 10),    # right arm
    (5, 11), (6, 12),   # torso sides
    (11, 12),           # hips
    (11, 13), (13, 15), # left leg
    (12, 14), (14, 16), # right leg
]


class _Landmark:
    """Lightweight stand-in for mediapipe NormalizedLandmark."""
    __slots__ = ('x', 'y', 'z', 'visibility')
    def __init__(self, x=0.0, y=0.0, z=0.0, visibility=0.0):
        self.x = x
        self.y = y
        self.z = z
        self.visibility = visibility


class _DetectionResult:
    """Lightweight stand-in for mediapipe PoseLandmarkerResult."""
    def __init__(self, pose_landmarks):
        self.pose_landmarks = pose_landmarks  # list[list[_Landmark]]


def _yolo_to_detection_result(results, h, w):
    """Convert Ultralytics YOLO-Pose results → MediaPipe-compatible _DetectionResult."""
    pose_landmarks_list = []
    for result in results:
        if result.keypoints is None or result.keypoints.data is None:
            continue
        for person_kps in result.keypoints.data:          # (17, 3)
            landmarks = [_Landmark() for _ in range(33)]   # all invisible
            for coco_idx, mp_idx in COCO_TO_MEDIAPIPE.items():
                px, py, conf = person_kps[coco_idx]
                landmarks[mp_idx] = _Landmark(
                    x=float(px) / w,
                    y=float(py) / h,
                    z=0.0,
                    visibility=float(conf),
                )
            pose_landmarks_list.append(landmarks)
    return _DetectionResult(pose_landmarks_list)


class BarbellDetector:
    """
    Detects a barbell in video frames by finding real Hough lines near
    the wrist connection and correlating their motion with body movement.
    Constraints applied:
      1. Near-horizontal — candidates tilted > BARBELL_MAX_TILT_DEG are discarded.
      2. Consistent motion — large frame-to-frame jumps are rejected.
      3. Body-motion correlation — among multiple candidates the line that
         moves when the body moves (and stays still when it doesn't) wins.
    """

    def __init__(self, history_size=BARBELL_HISTORY_SIZE):
        self.barbell_history = deque(maxlen=history_size)
        self.last_center = None          # previous-frame centre (pixels)
        self.frame_diagonal = None       # cached √(w²+h²)
        # Body-motion tracking (used by detect_barbell_near_wrists)
        self.prev_body_angles = None     # compute_body_angles() output from prev frame
        self.prev_line_candidates = []   # Hough-line candidates from prev frame

    # ------------------------------------------------------------------
    # Constraint helpers
    # ------------------------------------------------------------------

    def _is_horizontal(self, angle_deg):
        """Return True if the angle is within BARBELL_MAX_TILT_DEG of horizontal."""
        tilt = abs(angle_deg)
        if tilt > 90:
            tilt = 180 - tilt
        return tilt <= BARBELL_MAX_TILT_DEG

    def _is_motion_consistent(self, center):
        """Return True if *center* is close enough to the previous detection."""
        if self.last_center is None:
            return True  # first frame — accept anything
        dist = np.sqrt((center[0] - self.last_center[0]) ** 2 +
                       (center[1] - self.last_center[1]) ** 2)
        return dist <= BARBELL_MAX_JUMP_FRAC * self.frame_diagonal

    def _shoulder_width_px(self, pose_landmarks, h, w):
        """Return shoulder width in pixels, or None."""
        if pose_landmarks is None or len(pose_landmarks) <= RIGHT_SHOULDER:
            return None
        ls = pose_landmarks[LEFT_SHOULDER]
        rs = pose_landmarks[RIGHT_SHOULDER]
        if ls.visibility < 0.5 or rs.visibility < 0.5:
            return None
        return np.sqrt(((ls.x - rs.x) * w) ** 2 + ((ls.y - rs.y) * h) ** 2)

    # ------------------------------------------------------------------
    # Wrist-guided Hough line detection (primary)
    # ------------------------------------------------------------------

    def detect_barbell_near_wrists(self, frame, pose_landmarks, h, w,
                                   curr_body_angles=None):
        """
        Detect the barbell as a *real* Hough line near the wrist area.

        Supports both two-wrist and single-wrist modes:
          - Two wrists visible → ROI spans both wrists, midpoint used as anchor.
          - One wrist visible  → ROI centred on the visible wrist, shoulder
            width used as the reference distance for sizing.

        Steps
        -----
        1. Locate visible wrist(s) → define a padded search ROI.
        2. Run Canny + HoughLinesP inside that ROI to find real edges.
        3. Keep only near-horizontal lines (within BARBELL_MAX_TILT_DEG).
        4. Score each candidate by:
             a) length  (longer → better)
             b) proximity to the wrist anchor (closer → better)
             c) motion consistency with the body (see is_body_move).
        5. Return the best candidate, or None if nothing convincing.
        """
        if pose_landmarks is None or len(pose_landmarks) <= RIGHT_WRIST:
            return None

        lw = pose_landmarks[LEFT_WRIST]
        rw = pose_landmarks[RIGHT_WRIST]
        lw_vis = lw.visibility >= 0.5
        rw_vis = rw.visibility >= 0.5

        if not lw_vis and not rw_vis:
            return None  # need at least one wrist

        # --- Determine wrist mode and reference quantities ---
        sw = self._shoulder_width_px(pose_landmarks, h, w)  # may be None
        single_wrist = not (lw_vis and rw_vis)

        if lw_vis and rw_vis:
            # ---- Two-wrist mode ----
            lw_px = np.array([lw.x * w, lw.y * h])
            rw_px = np.array([rw.x * w, rw.y * h])
            wrist_anchor = (lw_px + rw_px) / 2          # midpoint
            wrist_dist = np.linalg.norm(rw_px - lw_px)
            if wrist_dist < 10:
                return None
            # Grip / shoulder sanity check (only meaningful with two wrists)
            if sw is not None and sw > 0:
                ratio = wrist_dist / sw
                if ratio < BARBELL_GRIP_MIN_SHOULDER_RATIO or ratio > BARBELL_GRIP_MAX_SHOULDER_RATIO:
                    return None
            ref_dist = wrist_dist  # reference length for ROI sizing / min_len
        else:
            # ---- Single-wrist mode ----
            if lw_vis:
                vis_px = np.array([lw.x * w, lw.y * h])
            else:
                vis_px = np.array([rw.x * w, rw.y * h])
            lw_px = vis_px if lw_vis else None
            rw_px = vis_px if rw_vis else None
            wrist_anchor = vis_px
            # Use shoulder width (or a fixed fallback) as reference distance
            ref_dist = sw if (sw is not None and sw > 0) else 150.0
            wrist_dist = ref_dist  # used later for extension / min_len

        # --- Define search ROI around wrist area ---
        pad_x = max(int(ref_dist * 0.8), 80)
        pad_y = max(int(ref_dist * 0.4), 50)
        if lw_px is not None and rw_px is not None:
            roi_x1 = max(0, int(min(lw_px[0], rw_px[0]) - pad_x))
            roi_y1 = max(0, int(min(lw_px[1], rw_px[1]) - pad_y))
            roi_x2 = min(w, int(max(lw_px[0], rw_px[0]) + pad_x))
            roi_y2 = min(h, int(max(lw_px[1], rw_px[1]) + pad_y))
        else:
            # Single wrist: centre the ROI on the visible wrist
            roi_x1 = max(0, int(wrist_anchor[0] - pad_x))
            roi_y1 = max(0, int(wrist_anchor[1] - pad_y))
            roi_x2 = min(w, int(wrist_anchor[0] + pad_x))
            roi_y2 = min(h, int(wrist_anchor[1] + pad_y))

        roi = frame[roi_y1:roi_y2, roi_x1:roi_x2]
        if roi.size == 0:
            return None

        # --- Edge + Hough line detection inside ROI ---
        gray_roi = cv2.cvtColor(roi, cv2.COLOR_RGB2GRAY)
        edges = cv2.Canny(gray_roi, 50, 150)
        kernel = np.ones((3, 3), np.uint8)
        edges = cv2.dilate(edges, kernel, iterations=1)

        min_len = max(50, int(ref_dist * 0.3))
        lines = cv2.HoughLinesP(
            edges, rho=1, theta=np.pi / 180,
            threshold=50, minLineLength=min_len, maxLineGap=20
        )

        if lines is None:
            # No real line found — update state and bail
            if curr_body_angles is not None:
                self.prev_body_angles = curr_body_angles
            self.prev_line_candidates = []
            return None

        # --- Body motion state ---
        if curr_body_angles is None:
            curr_body_angles = compute_body_angles(pose_landmarks, h, w)
        body_moved, angle_delta, wrist_dy = is_body_move(
            self.prev_body_angles, curr_body_angles
        )

        # --- Build candidate list ---
        candidates = []
        for line in lines:
            x1_r, y1_r, x2_r, y2_r = line[0]
            # Convert ROI coords → frame coords
            x1 = x1_r + roi_x1
            y1 = y1_r + roi_y1
            x2 = x2_r + roi_x1
            y2 = y2_r + roi_y1

            dx = x2 - x1
            dy = y2 - y1
            length = np.sqrt(dx ** 2 + dy ** 2)
            angle = np.degrees(np.arctan2(dy, dx))

            if not self._is_horizontal(angle):
                continue
            if length < min_len:
                continue

            center_f = np.array([(x1 + x2) / 2.0, (y1 + y2) / 2.0])
            dist_to_wrists = float(np.linalg.norm(center_f - wrist_anchor))

            # Base score: longer + closer to wrist midpoint
            score = length * 0.5 - dist_to_wrists

            candidates.append({
                'endpoints': ((int(x1), int(y1)), (int(x2), int(y2))),
                'center': (int(center_f[0]), int(center_f[1])),
                '_center_f': center_f,          # float, kept for tracking
                'angle': angle,
                'length': length,
                'dist_to_wrists': dist_to_wrists,
                'score': score,
            })

        if not candidates:
            if curr_body_angles is not None:
                self.prev_body_angles = curr_body_angles
            self.prev_line_candidates = []
            return None

        # --- Motion-correlation tiebreaker ---
        if self.prev_line_candidates and len(candidates) > 1:
            for cand in candidates:
                # Find this candidate's closest match from the previous frame
                best_d = float('inf')
                best_prev = None
                for pc in self.prev_line_candidates:
                    d = np.linalg.norm(cand['_center_f'] - pc['_center_f'])
                    if d < best_d:
                        best_d = d
                        best_prev = pc

                if best_prev is None or best_d > self.frame_diagonal * 0.15:
                    continue  # no match in previous frame — no bonus / penalty

                cand_dy = cand['_center_f'][1] - best_prev['_center_f'][1]

                if body_moved:
                    # Body moved — reward lines that move with the wrists
                    if wrist_dy is not None and abs(wrist_dy) > 2:
                        # Same vertical direction as wrist
                        if (wrist_dy > 0 and cand_dy > 0) or (wrist_dy < 0 and cand_dy < 0):
                            cand['score'] += 80
                        elif abs(cand_dy) < 2:
                            # Line stayed put while body moved → likely rack
                            cand['score'] -= 40
                    else:
                        # Wrist dy unavailable; just reward lines that moved
                        if abs(cand_dy) > 3:
                            cand['score'] += 40
                else:
                    # Body didn't move — reward lines that also stayed still
                    if abs(cand_dy) < 5:
                        cand['score'] += 50

        # --- Select the best candidate ---
        best = max(candidates, key=lambda c: c['score'])

        # --- Update tracking state ---
        self.prev_body_angles = curr_body_angles
        self.prev_line_candidates = candidates

        # --- Build result dict ---
        (x1, y1), (x2, y2) = best['endpoints']
        # Ensure left < right for consistent extension direction
        if x1 > x2:
            x1, y1, x2, y2 = x2, y2, x1, y1

        line_dx = x2 - x1
        line_dy = y2 - y1
        line_len = best['length']
        ux = line_dx / line_len
        uy = line_dy / line_len
        extension = ref_dist * 0.3
        left_end = (int(x1 - extension * ux), int(y1 - extension * uy))
        right_end = (int(x2 + extension * ux), int(y2 + extension * uy))

        grip_left = (int(lw_px[0]), int(lw_px[1])) if lw_px is not None else None
        grip_right = (int(rw_px[0]), int(rw_px[1])) if rw_px is not None else None

        return {
            'left_grip': grip_left,
            'right_grip': grip_right,
            'single_wrist': single_wrist,
            'left_end': left_end,
            'right_end': right_end,
            'center': best['center'],
            'angle': best['angle'],
            'length': best['length'],
            'all_candidates': candidates,
        }


    # ------------------------------------------------------------------
    # Confidence scoring
    # ------------------------------------------------------------------

    def _compute_confidence(self, barbell_data, method):
        """
        Compute a 0.0 – 1.0 confidence score for the current barbell detection.

        Factors (each mapped to 0–1, then combined as a weighted average):
          1. line_length   – longer Hough line → higher confidence
          2. proximity     – closer to the wrist midpoint → higher confidence
          3. tilt          – more horizontal → higher confidence
          4. score_margin  – bigger gap between best and 2nd-best → higher confidence
          5. n_candidates  – fewer candidates in the ROI → less ambiguity
          6. temporal_hit  – fraction of recent history frames that had a detection
          7. method_bonus  – fresh 'wrist_lines' detection > 'history' fallback
        """
        scores = {}
        weights = {}

        # --- 1. Line length (saturates at frame_diagonal * 0.25) ---
        max_useful_len = self.frame_diagonal * 0.25 if self.frame_diagonal else 500
        raw_len = barbell_data.get('length', 0)
        scores['line_length'] = min(raw_len / max_useful_len, 1.0)
        weights['line_length'] = 2.0

        # --- 2. Proximity to wrist midpoint (0 = on top, worse as it grows) ---
        #     Use the best candidate's dist_to_wrists from all_candidates.
        all_cands = barbell_data.get('all_candidates', [])
        if all_cands:
            best_cand = max(all_cands, key=lambda c: c['score'])
            dtw = best_cand.get('dist_to_wrists', 0)
        else:
            dtw = 0
        # Normalise: 0 px → 1.0, ≥ half-diagonal → 0.0
        half_diag = (self.frame_diagonal * 0.5) if self.frame_diagonal else 500
        scores['proximity'] = max(1.0 - dtw / half_diag, 0.0)
        weights['proximity'] = 2.0

        # --- 3. Tilt from horizontal (0° → 1.0, ≥ BARBELL_MAX_TILT_DEG → 0.0) ---
        angle = barbell_data.get('angle', 0)
        tilt = abs(angle)
        if tilt > 90:
            tilt = 180 - tilt
        scores['tilt'] = max(1.0 - tilt / BARBELL_MAX_TILT_DEG, 0.0)
        weights['tilt'] = 1.5

        # --- 4. Score margin between best and 2nd-best candidate ---
        if len(all_cands) >= 2:
            sorted_scores = sorted([c['score'] for c in all_cands], reverse=True)
            margin = sorted_scores[0] - sorted_scores[1]
            # Normalise: margin of 0 → 0.0, margin ≥ 150 → 1.0
            scores['score_margin'] = min(margin / 150.0, 1.0)
        elif len(all_cands) == 1:
            scores['score_margin'] = 1.0   # only one candidate → no ambiguity
        else:
            scores['score_margin'] = 0.0
        weights['score_margin'] = 1.5

        # --- 5. Number of candidates (1 is ideal, many = noisy) ---
        n = len(all_cands)
        if n == 0:
            scores['n_candidates'] = 0.0
        elif n <= 3:
            scores['n_candidates'] = 1.0
        else:
            scores['n_candidates'] = max(1.0 - (n - 3) / 20.0, 0.0)
        weights['n_candidates'] = 1.0

        # --- 6. Temporal consistency (fraction of history with detections) ---
        hist_len = self.barbell_history.maxlen or BARBELL_HISTORY_SIZE
        if hist_len > 0:
            scores['temporal'] = len(self.barbell_history) / hist_len
        else:
            scores['temporal'] = 0.0
        weights['temporal'] = 1.5

        # --- 7. Method bonus ---
        if method == 'wrist_lines':
            scores['method'] = 1.0
        elif method == 'history':
            scores['method'] = 0.3
        else:
            scores['method'] = 0.5
        weights['method'] = 1.0

        # --- 8. Wrist count (two wrists = full score, single wrist = reduced) ---
        if barbell_data.get('single_wrist'):
            scores['wrist_count'] = 0.4
        else:
            scores['wrist_count'] = 1.0
        weights['wrist_count'] = 1.5

        # --- Weighted average ---
        total_w = sum(weights.values())
        confidence = sum(scores[k] * weights[k] for k in scores) / total_w

        return round(float(np.clip(confidence, 0.0, 1.0)), 3), scores

    # ------------------------------------------------------------------
    # Temporal smoothing
    # ------------------------------------------------------------------

    def smooth_detection(self, current_detection, history):
        """Apply temporal smoothing to reduce jitter."""
        if current_detection is None:
            return None

        history.append(current_detection)

        if len(history) < 2:
            return current_detection

        avg_cx = np.mean([d['center'][0] for d in history])
        avg_cy = np.mean([d['center'][1] for d in history])
        avg_angle = np.mean([d['angle'] for d in history])

        smoothed = current_detection.copy()
        smoothed['center'] = (int(avg_cx), int(avg_cy))
        smoothed['angle'] = avg_angle
        return smoothed

    # ------------------------------------------------------------------
    # Main entry point
    # ------------------------------------------------------------------

    def detect(self, frame, pose_landmarks=None):
        """
        Main detection method.  Uses wrist-guided Hough line detection:
          – Finds real lines near the wrist connection.
          – Picks the one whose motion correlates with body movement.
        Falls back to history when motion-consistency is violated.
        """
        h, w = frame.shape[:2]
        self.frame_diagonal = np.sqrt(h ** 2 + w ** 2)

        # Pre-compute body angles once (reused by the detector)
        curr_body_angles = None
        if pose_landmarks is not None:
            curr_body_angles = compute_body_angles(pose_landmarks, h, w)

        barbell_data = None

        # --- Wrist-guided Hough line detection ---
        if pose_landmarks is not None and len(pose_landmarks) > RIGHT_WRIST:
            barbell_data = self.detect_barbell_near_wrists(
                frame, pose_landmarks, h, w, curr_body_angles
            )
            if barbell_data is not None:
                barbell_data['method'] = 'wrist_lines'

        # --- Constraint: motion consistency ---
        if barbell_data is not None:
            if not self._is_motion_consistent(barbell_data['center']):
                # Jump too large → reuse last known position
                if self.barbell_history:
                    barbell_data = self.barbell_history[-1].copy()
                    barbell_data['method'] = 'history'
                else:
                    barbell_data = None

        # Smooth & update state
        if barbell_data is not None:
            barbell_data = self.smooth_detection(barbell_data, self.barbell_history)
            self.last_center = barbell_data['center']

        # --- Compute confidence ---
        if barbell_data is not None:
            method = barbell_data.get('method', '?')
            confidence, confidence_details = self._compute_confidence(barbell_data, method)
            barbell_data['confidence'] = confidence
            barbell_data['confidence_details'] = confidence_details

        # Keep body-angle history up to date
        if curr_body_angles is not None:
            self.prev_body_angles = curr_body_angles

        return {
            'barbell': barbell_data
        }


def calculate_barbell_body_angle(barbell_data, mid_shoulder, mid_hip, h, w):
    """
    Calculate angles between barbell and body parts.
    Returns various angle measurements useful for exercise analysis.
    """
    if barbell_data is None or mid_shoulder is None or mid_hip is None:
        return None

    barbell_center = barbell_data['center']
    barbell_angle = barbell_data['angle']

    # Convert normalized coordinates to pixels
    shoulder_pt = (int(mid_shoulder['x'] * w), int(mid_shoulder['y'] * h))
    hip_pt = (int(mid_hip['x'] * w), int(mid_hip['y'] * h))

    # Vector from hip to shoulder (spine direction)
    spine_vec = np.array([shoulder_pt[0] - hip_pt[0], shoulder_pt[1] - hip_pt[1]])
    spine_length = np.linalg.norm(spine_vec)

    if spine_length == 0:
        return None

    # Calculate spine angle from vertical
    spine_angle = np.degrees(np.arctan2(spine_vec[0], -spine_vec[1]))  # Negative y because image coords

    # Vector from hip to barbell center
    hip_to_barbell = np.array([barbell_center[0] - hip_pt[0], barbell_center[1] - hip_pt[1]])

    # Angle from hip to barbell relative to vertical
    hip_barbell_angle = np.degrees(np.arctan2(hip_to_barbell[0], -hip_to_barbell[1]))

    # Barbell position relative to spine (forward/back)
    # Project barbell position onto spine perpendicular
    if spine_length > 0:
        spine_unit = spine_vec / spine_length
        spine_perp = np.array([-spine_unit[1], spine_unit[0]])  # Perpendicular vector

        # Vector from mid-spine to barbell
        mid_spine_pt = ((shoulder_pt[0] + hip_pt[0]) // 2, (shoulder_pt[1] + hip_pt[1]) // 2)
        to_barbell = np.array([barbell_center[0] - mid_spine_pt[0], barbell_center[1] - mid_spine_pt[1]])

        # Forward/back offset (positive = forward, negative = back)
        forward_offset = np.dot(to_barbell, spine_perp)
    else:
        forward_offset = 0

    # Calculate how horizontal the barbell is (0 = perfectly horizontal)
    barbell_tilt = abs(barbell_angle)
    if barbell_tilt > 90:
        barbell_tilt = 180 - barbell_tilt

    return {
        'barbell_angle': barbell_angle,
        'barbell_tilt': barbell_tilt,  # Deviation from horizontal
        'spine_angle': spine_angle,
        'hip_barbell_angle': hip_barbell_angle,
        'forward_offset': forward_offset,  # How far forward/back the barbell is
        'is_bar_level': barbell_tilt < 5.0,  # Within 5 degrees of horizontal
    }


# Global barbell detector instance
barbell_detector = BarbellDetector(history_size=BARBELL_HISTORY_SIZE)


def calculate_spine_points(pose_landmarks):
    """
    Calculate virtual spine points from pose landmarks.
    Returns: mid_shoulder, mid_spine, mid_hip points and visibility score
    """
    if len(pose_landmarks) < 25:
        return None, None, None, 0

    left_shoulder = pose_landmarks[LEFT_SHOULDER]
    right_shoulder = pose_landmarks[RIGHT_SHOULDER]
    left_hip = pose_landmarks[LEFT_HIP]
    right_hip = pose_landmarks[RIGHT_HIP]

    # Check visibility of key landmarks
    min_visibility = min(
        left_shoulder.visibility,
        right_shoulder.visibility,
        left_hip.visibility,
        right_hip.visibility
    )

    if min_visibility < 0.5:
        return None, None, None, min_visibility

    # Calculate mid-shoulder point (neck area)
    mid_shoulder = {
        'x': (left_shoulder.x + right_shoulder.x) / 2,
        'y': (left_shoulder.y + right_shoulder.y) / 2,
        'z': (left_shoulder.z + right_shoulder.z) / 2
    }

    # Calculate mid-hip point
    mid_hip = {
        'x': (left_hip.x + right_hip.x) / 2,
        'y': (left_hip.y + right_hip.y) / 2,
        'z': (left_hip.z + right_hip.z) / 2
    }

    # Calculate mid-spine point (between shoulders and hips)
    mid_spine = {
        'x': (mid_shoulder['x'] + mid_hip['x']) / 2,
        'y': (mid_shoulder['y'] + mid_hip['y']) / 2,
        'z': (mid_shoulder['z'] + mid_hip['z']) / 2
    }

    return mid_shoulder, mid_spine, mid_hip, min_visibility



def calculate_leg_spine_angle(pose_landmarks, mid_shoulder, mid_hip, h=1, w=1):
    """
    Calculate the angle between the spine and each thigh.
    h, w: frame height and width – used to convert normalised coords to pixels
          so the angle is not distorted by the video aspect ratio.
    Returns: (left_angle, right_angle) in degrees (180 = straight line, <180 = bent).
             Either value may be None if the corresponding landmarks are not visible.
    """
    if mid_shoulder is None or mid_hip is None:
        return None, None

    if len(pose_landmarks) < 27:
        return None, None

    left_knee = pose_landmarks[LEFT_KNEE]
    right_knee = pose_landmarks[RIGHT_KNEE]

    # Spine vector (hip → shoulder) is shared by both sides
    spine_vec = np.array([
        (mid_shoulder['x'] - mid_hip['x']) * w,
        (mid_shoulder['y'] - mid_hip['y']) * h
    ])
    spine_len = np.linalg.norm(spine_vec)
    if spine_len == 0:
        return None, None

    left_angle = None
    right_angle = None

    # Left leg
    if left_knee.visibility >= 0.5:
        leg_vec = np.array([
            (left_knee.x - mid_hip['x']) * w,
            (left_knee.y - mid_hip['y']) * h
        ])
        leg_len = np.linalg.norm(leg_vec)
        if leg_len > 0:
            cos_a = np.clip(np.dot(spine_vec, leg_vec) / (spine_len * leg_len), -1.0, 1.0)
            left_angle = np.degrees(np.arccos(cos_a))

    # Right leg
    if right_knee.visibility >= 0.5:
        leg_vec = np.array([
            (right_knee.x - mid_hip['x']) * w,
            (right_knee.y - mid_hip['y']) * h
        ])
        leg_len = np.linalg.norm(leg_vec)
        if leg_len > 0:
            cos_a = np.clip(np.dot(spine_vec, leg_vec) / (spine_len * leg_len), -1.0, 1.0)
            right_angle = np.degrees(np.arccos(cos_a))

    return left_angle, right_angle


def calculate_foot_leg_angle(pose_landmarks, h=1, w=1):
    """
    Calculate the angle at the ankle (between shin and foot) for both feet.
    h, w: frame height and width – used to convert normalised coords to pixels
          so the angle is not distorted by the video aspect ratio.
    Returns: (left_angle, right_angle) in degrees.
             90 = foot perpendicular to shin, <90 = dorsiflexion, >90 = plantarflexion.
             Either value may be None if the corresponding landmarks are not visible.
    """
    if len(pose_landmarks) < 33:
        return None, None

    left_knee = pose_landmarks[LEFT_KNEE]
    right_knee = pose_landmarks[RIGHT_KNEE]
    left_ankle = pose_landmarks[LEFT_ANKLE]
    right_ankle = pose_landmarks[RIGHT_ANKLE]

    # Foot landmarks (heel and toe)
    LEFT_HEEL = 29
    RIGHT_HEEL = 30
    LEFT_FOOT_INDEX = 31
    RIGHT_FOOT_INDEX = 32

    left_angle = None
    right_angle = None

    # Calculate left ankle angle if visible
    if (left_knee.visibility >= 0.5 and left_ankle.visibility >= 0.5 and
            pose_landmarks[LEFT_HEEL].visibility >= 0.5):
        shin_vec = np.array([
            (left_knee.x - left_ankle.x) * w,
            (left_knee.y - left_ankle.y) * h
        ])
        foot_vec = np.array([
            (pose_landmarks[LEFT_FOOT_INDEX].x - left_ankle.x) * w,
            (pose_landmarks[LEFT_FOOT_INDEX].y - left_ankle.y) * h
        ])
        shin_len = np.linalg.norm(shin_vec)
        foot_len = np.linalg.norm(foot_vec)
        if shin_len > 0 and foot_len > 0:
            dot_product = np.dot(shin_vec, foot_vec)
            cos_angle = np.clip(dot_product / (shin_len * foot_len), -1.0, 1.0)
            left_angle = np.degrees(np.arccos(cos_angle))

    # Calculate right ankle angle if visible
    if (right_knee.visibility >= 0.5 and right_ankle.visibility >= 0.5 and
            pose_landmarks[RIGHT_HEEL].visibility >= 0.5):
        shin_vec = np.array([
            (right_knee.x - right_ankle.x) * w,
            (right_knee.y - right_ankle.y) * h
        ])
        foot_vec = np.array([
            (pose_landmarks[RIGHT_FOOT_INDEX].x - right_ankle.x) * w,
            (pose_landmarks[RIGHT_FOOT_INDEX].y - right_ankle.y) * h
        ])
        shin_len = np.linalg.norm(shin_vec)
        foot_len = np.linalg.norm(foot_vec)
        if shin_len > 0 and foot_len > 0:
            dot_product = np.dot(shin_vec, foot_vec)
            cos_angle = np.clip(dot_product / (shin_len * foot_len), -1.0, 1.0)
            right_angle = np.degrees(np.arccos(cos_angle))

    return left_angle, right_angle


def calculate_knee_angle(pose_landmarks, h=1, w=1):
    """
    Calculate the angle at the knee (between thigh and shin) for both legs.
    h, w: frame height and width – used to convert normalised coords to pixels
          so the angle is not distorted by the video aspect ratio.
    Returns: (left_angle, right_angle) in degrees (180 = straight leg, <180 = bent).
             Either value may be None if the corresponding landmarks are not visible.
    """
    if len(pose_landmarks) < 29:
        return None, None

    left_hip = pose_landmarks[LEFT_HIP]
    right_hip = pose_landmarks[RIGHT_HIP]
    left_knee = pose_landmarks[LEFT_KNEE]
    right_knee = pose_landmarks[RIGHT_KNEE]
    left_ankle = pose_landmarks[LEFT_ANKLE]
    right_ankle = pose_landmarks[RIGHT_ANKLE]

    left_angle = None
    right_angle = None

    # Calculate left knee angle if visible
    if (left_hip.visibility >= 0.5 and left_knee.visibility >= 0.5 and left_ankle.visibility >= 0.5):
        thigh_vec = np.array([
            (left_hip.x - left_knee.x) * w,
            (left_hip.y - left_knee.y) * h
        ])
        shin_vec = np.array([
            (left_ankle.x - left_knee.x) * w,
            (left_ankle.y - left_knee.y) * h
        ])
        thigh_len = np.linalg.norm(thigh_vec)
        shin_len = np.linalg.norm(shin_vec)
        if thigh_len > 0 and shin_len > 0:
            dot_product = np.dot(thigh_vec, shin_vec)
            cos_angle = np.clip(dot_product / (thigh_len * shin_len), -1.0, 1.0)
            left_angle = np.degrees(np.arccos(cos_angle))

    # Calculate right knee angle if visible
    if (right_hip.visibility >= 0.5 and right_knee.visibility >= 0.5 and right_ankle.visibility >= 0.5):
        thigh_vec = np.array([
            (right_hip.x - right_knee.x) * w,
            (right_hip.y - right_knee.y) * h
        ])
        shin_vec = np.array([
            (right_ankle.x - right_knee.x) * w,
            (right_ankle.y - right_knee.y) * h
        ])
        thigh_len = np.linalg.norm(thigh_vec)
        shin_len = np.linalg.norm(shin_vec)
        if thigh_len > 0 and shin_len > 0:
            dot_product = np.dot(thigh_vec, shin_vec)
            cos_angle = np.clip(dot_product / (thigh_len * shin_len), -1.0, 1.0)
            right_angle = np.degrees(np.arccos(cos_angle))

    return left_angle, right_angle


def compute_body_angles(pose_landmarks, h=1, w=1):
    """
    Compute a compact set of key body angles from pose landmarks.
    Used by is_body_move() to detect frame-to-frame body movement.

    Returns a dict of angle values (degrees), or None if landmarks are
    insufficient.  Also includes 'wrist_mid_y' (pixels) when both wrists
    are visible so that vertical barbell displacement can be correlated
    directly with wrist movement.
    """
    if pose_landmarks is None or len(pose_landmarks) < 29:
        return None

    angles = {}

    ls = pose_landmarks[LEFT_SHOULDER]
    rs = pose_landmarks[RIGHT_SHOULDER]
    lh = pose_landmarks[LEFT_HIP]
    rh = pose_landmarks[RIGHT_HIP]
    lk = pose_landmarks[LEFT_KNEE]
    rk = pose_landmarks[RIGHT_KNEE]
    la = pose_landmarks[LEFT_ANKLE]
    ra = pose_landmarks[RIGHT_ANKLE]

    # Spine angle from vertical
    if all(l.visibility >= 0.5 for l in [ls, rs, lh, rh]):
        ms_x = (ls.x + rs.x) / 2 * w
        ms_y = (ls.y + rs.y) / 2 * h
        mh_x = (lh.x + rh.x) / 2 * w
        mh_y = (lh.y + rh.y) / 2 * h
        angles['spine_angle'] = np.degrees(np.arctan2(ms_x - mh_x, -(ms_y - mh_y)))

    # Left knee angle
    if all(l.visibility >= 0.5 for l in [lh, lk, la]):
        tv = np.array([(lh.x - lk.x) * w, (lh.y - lk.y) * h])
        sv = np.array([(la.x - lk.x) * w, (la.y - lk.y) * h])
        tl, sl = np.linalg.norm(tv), np.linalg.norm(sv)
        if tl > 0 and sl > 0:
            angles['left_knee'] = np.degrees(np.arccos(
                np.clip(np.dot(tv, sv) / (tl * sl), -1, 1)))

    # Right knee angle
    if all(l.visibility >= 0.5 for l in [rh, rk, ra]):
        tv = np.array([(rh.x - rk.x) * w, (rh.y - rk.y) * h])
        sv = np.array([(ra.x - rk.x) * w, (ra.y - rk.y) * h])
        tl, sl = np.linalg.norm(tv), np.linalg.norm(sv)
        if tl > 0 and sl > 0:
            angles['right_knee'] = np.degrees(np.arccos(
                np.clip(np.dot(tv, sv) / (tl * sl), -1, 1)))

    # Left leg-spine angle
    if all(l.visibility >= 0.5 for l in [ls, rs, lh, rh, lk]):
        ms = np.array([(ls.x + rs.x) / 2 * w, (ls.y + rs.y) / 2 * h])
        mh = np.array([(lh.x + rh.x) / 2 * w, (lh.y + rh.y) / 2 * h])
        sv = ms - mh
        lv = np.array([lk.x * w - mh[0], lk.y * h - mh[1]])
        s_len, l_len = np.linalg.norm(sv), np.linalg.norm(lv)
        if s_len > 0 and l_len > 0:
            angles['left_leg_spine'] = np.degrees(np.arccos(
                np.clip(np.dot(sv, lv) / (s_len * l_len), -1, 1)))

    # Right leg-spine angle
    if all(l.visibility >= 0.5 for l in [ls, rs, lh, rh, rk]):
        ms = np.array([(ls.x + rs.x) / 2 * w, (ls.y + rs.y) / 2 * h])
        mh = np.array([(lh.x + rh.x) / 2 * w, (lh.y + rh.y) / 2 * h])
        sv = ms - mh
        rv = np.array([rk.x * w - mh[0], rk.y * h - mh[1]])
        s_len, r_len = np.linalg.norm(sv), np.linalg.norm(rv)
        if s_len > 0 and r_len > 0:
            angles['right_leg_spine'] = np.degrees(np.arccos(
                np.clip(np.dot(sv, rv) / (s_len * r_len), -1, 1)))

    # Wrist midpoint Y (pixels) — used for direct motion correlation
    lw = pose_landmarks[LEFT_WRIST]
    rw = pose_landmarks[RIGHT_WRIST]
    if lw.visibility >= 0.5 and rw.visibility >= 0.5:
        angles['wrist_mid_y'] = (lw.y + rw.y) / 2 * h

    return angles if angles else None


def is_body_move(prev_body_angles, curr_body_angles,
                 threshold=BODY_MOVE_ANGLE_THRESHOLD):
    """
    Determine whether the body moved between two consecutive frames by
    comparing key body angles.

    Args:
        prev_body_angles: dict from compute_body_angles() for the previous frame
        curr_body_angles: dict from compute_body_angles() for the current frame
        threshold: minimum average |Δangle| (degrees) to count as "moved"

    Returns:
        (moved, angle_delta, wrist_dy)
        - moved (bool):      True when the average absolute angle change ≥ threshold
        - angle_delta (float): signed mean of all angle deltas (positive = body
                               is generally extending / standing up)
        - wrist_dy (float|None): change in wrist-midpoint Y in pixels
                                 (positive = wrists moved downward in image)
    """
    if prev_body_angles is None or curr_body_angles is None:
        return False, 0.0, None

    deltas = []
    for key in ('spine_angle', 'left_knee', 'right_knee',
                'left_leg_spine', 'right_leg_spine'):
        pv = prev_body_angles.get(key)
        cv = curr_body_angles.get(key)
        if pv is not None and cv is not None:
            deltas.append(cv - pv)

    if not deltas:
        return False, 0.0, None

    avg_abs = float(np.mean(np.abs(deltas)))
    avg_signed = float(np.mean(deltas))

    wrist_dy = None
    pw = prev_body_angles.get('wrist_mid_y')
    cw = curr_body_angles.get('wrist_mid_y')
    if pw is not None and cw is not None:
        wrist_dy = cw - pw

    return avg_abs >= threshold, avg_signed, wrist_dy


# Drawing utility function
def draw_landmarks_on_image(rgb_image, detection_result):
    pose_landmarks_list = detection_result.pose_landmarks
    annotated_image = np.copy(rgb_image)
    h, w, _ = annotated_image.shape

    spine_data = None  # Store spine analysis data for return
    barbell_result = None  # Store barbell detection result

    # Loop through the detected poses to visualize
    for pose_landmarks in pose_landmarks_list:
        # Draw connections (skeleton lines)
        for connection in POSE_CONNECTIONS:
            start_idx, end_idx = connection
            if start_idx < len(pose_landmarks) and end_idx < len(pose_landmarks):
                start = pose_landmarks[start_idx]
                end = pose_landmarks[end_idx]

                # Only draw if both landmarks are visible
                if start.visibility > 0.5 and end.visibility > 0.5:
                    start_point = (int(start.x * w), int(start.y * h))
                    end_point = (int(end.x * w), int(end.y * h))
                    cv2.line(annotated_image, start_point, end_point, (0, 255, 0), 2)

        # Draw landmarks (dots)
        for landmark in pose_landmarks:
            if landmark.visibility > 0.5:
                cx, cy = int(landmark.x * w), int(landmark.y * h)
                cv2.circle(annotated_image, (cx, cy), 5, (255, 0, 0), -1)
                cv2.circle(annotated_image, (cx, cy), 5, (0, 0, 255), 1)

        # Barbell detection
        if BARBELL_DETECTION_ENABLED:
            barbell_result = barbell_detector.detect(rgb_image, pose_landmarks)

            # Draw barbell if detected
            if barbell_result['barbell'] is not None:
                barbell = barbell_result['barbell']

                # Draw ALL Hough line candidates (thin magenta lines)
                all_cands = barbell.get('all_candidates', [])
                for ci, cand in enumerate(all_cands):
                    (cx1, cy1), (cx2, cy2) = cand['endpoints']
                    # Thin magenta line for every candidate
                    cv2.line(annotated_image, (cx1, cy1), (cx2, cy2),
                             (255, 0, 255), 2)  # Magenta
                    # Small score label next to each candidate's centre
                    cc = cand['center']
                    cv2.putText(annotated_image,
                                f"{cand['score']:.0f}",
                                (cc[0] + 5, cc[1] - 5),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.4,
                                (255, 0, 255), 1)

                # Draw the barbell line
                if 'left_end' in barbell and 'right_end' in barbell:
                    # Extended barbell line
                    cv2.line(annotated_image, barbell['left_end'], barbell['right_end'],
                             (0, 165, 255), 4)  # Orange color

                    # Draw grip points
                    if barbell.get('left_grip') is not None:
                        cv2.circle(annotated_image, barbell['left_grip'], 8, (255, 255, 0), -1)
                    if barbell.get('right_grip') is not None:
                        cv2.circle(annotated_image, barbell['right_grip'], 8, (255, 255, 0), -1)

                elif 'endpoints' in barbell:
                    # Line detected from Hough transform
                    cv2.line(annotated_image, barbell['endpoints'][0], barbell['endpoints'][1],
                             (0, 165, 255), 4)

                # Draw barbell center
                cv2.circle(annotated_image, barbell['center'], 10, (0, 255, 255), -1)
                cv2.circle(annotated_image, barbell['center'], 10, (0, 0, 0), 2)

                # Draw detected plate circles if available
                if 'left_plate' in barbell:
                    px, py, pr = barbell['left_plate']
                    cv2.circle(annotated_image, (px, py), pr, (0, 200, 255), 3)
                if 'right_plate' in barbell:
                    px, py, pr = barbell['right_plate']
                    cv2.circle(annotated_image, (px, py), pr, (0, 200, 255), 3)

                # Label with detection method and confidence
                method = barbell.get('method', '?')
                if barbell.get('single_wrist'):
                    method += '/1w'
                conf = barbell.get('confidence', 0.0)
                # Color: green (≥0.7), orange (≥0.4), red (<0.4)
                if conf >= 0.7:
                    conf_color = (0, 255, 0)
                elif conf >= 0.4:
                    conf_color = (0, 165, 255)
                else:
                    conf_color = (0, 0, 255)
                label = f"BARBELL ({method}) conf={conf:.0%}"
                cv2.putText(annotated_image, label,
                            (barbell['center'][0] - 80, barbell['center'][1] - 15),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.6, conf_color, 2)

                # Show confidence breakdown (small text below the label)
                details = barbell.get('confidence_details', {})
                if details:
                    parts = [f"{k[:4]}={v:.2f}" for k, v in details.items()]
                    detail_text = "  ".join(parts)
                    cv2.putText(annotated_image, detail_text,
                                (barbell['center'][0] - 80, barbell['center'][1] + 5),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.35, conf_color, 1)


        # Calculate and draw spine points
        mid_shoulder, mid_spine, mid_hip, visibility = calculate_spine_points(pose_landmarks)

        if mid_shoulder is not None:
            # Convert to pixel coordinates
            shoulder_pt = (int(mid_shoulder['x'] * w), int(mid_shoulder['y'] * h))
            spine_pt = (int(mid_spine['x'] * w), int(mid_spine['y'] * h))
            hip_pt = (int(mid_hip['x'] * w), int(mid_hip['y'] * h))

            # Draw spine line (from shoulder to hip)
            cv2.line(annotated_image, shoulder_pt, hip_pt, (255, 255, 0), 2)  # Cyan line

            # Draw virtual spine points
            cv2.circle(annotated_image, shoulder_pt, 8, (0, 255, 255), -1)  # Yellow - mid shoulder
            cv2.circle(annotated_image, spine_pt, 8, (255, 0, 255), -1)  # Magenta - mid spine
            cv2.circle(annotated_image, hip_pt, 8, (0, 255, 255), -1)  # Yellow - mid hip

            # Add labels
            cv2.putText(annotated_image, "Neck", (shoulder_pt[0] + 10, shoulder_pt[1]),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
            cv2.putText(annotated_image, "Mid-Spine", (spine_pt[0] + 10, spine_pt[1]),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
            cv2.putText(annotated_image, "Hip", (hip_pt[0] + 10, hip_pt[1]),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)

            # Calculate joint angles (pass h, w for aspect-ratio-correct angles)
            left_leg_spine_angle, right_leg_spine_angle = calculate_leg_spine_angle(pose_landmarks, mid_shoulder, mid_hip, h, w)
            left_foot_leg_angle, right_foot_leg_angle = calculate_foot_leg_angle(pose_landmarks, h, w)
            left_knee_angle, right_knee_angle = calculate_knee_angle(pose_landmarks, h, w)

            # Store spine data
            spine_data = {
                'mid_shoulder': mid_shoulder,
                'mid_spine': mid_spine,
                'mid_hip': mid_hip,
                'left_leg_spine_angle': left_leg_spine_angle,
                'right_leg_spine_angle': right_leg_spine_angle,
                'left_foot_leg_angle': left_foot_leg_angle,
                'right_foot_leg_angle': right_foot_leg_angle,
                'left_knee_angle': left_knee_angle,
                'right_knee_angle': right_knee_angle
            }

            # Display joint angles
            y_offset = 40

            if left_leg_spine_angle is not None:
                cv2.putText(annotated_image, f"L Leg-Spine Angle: {left_leg_spine_angle:.1f} deg", (10, y_offset),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
                y_offset += 40

            if right_leg_spine_angle is not None:
                cv2.putText(annotated_image, f"R Leg-Spine Angle: {right_leg_spine_angle:.1f} deg", (10, y_offset),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
                y_offset += 40

            if left_foot_leg_angle is not None:
                cv2.putText(annotated_image, f"L Foot-Leg Angle: {left_foot_leg_angle:.1f} deg", (10, y_offset),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
                y_offset += 40

            if right_foot_leg_angle is not None:
                cv2.putText(annotated_image, f"R Foot-Leg Angle: {right_foot_leg_angle:.1f} deg", (10, y_offset),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
                y_offset += 40

            if left_knee_angle is not None:
                cv2.putText(annotated_image, f"L Knee Angle: {left_knee_angle:.1f} deg", (10, y_offset),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
                y_offset += 40

            if right_knee_angle is not None:
                cv2.putText(annotated_image, f"R Knee Angle: {right_knee_angle:.1f} deg", (10, y_offset),
                            cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
                y_offset += 40

            # Display barbell angles if detected
            if BARBELL_DETECTION_ENABLED and barbell_result is not None and barbell_result['barbell'] is not None:
                barbell = barbell_result['barbell']
                barbell_angles = calculate_barbell_body_angle(barbell, mid_shoulder, mid_hip, h, w)

                if barbell_angles is not None:
                    # Barbell tilt from horizontal
                    tilt_color = (0, 255, 0) if barbell_angles['is_bar_level'] else (0, 165, 255)
                    tilt_text = f"Barbell Tilt: {barbell_angles['barbell_tilt']:.1f} deg"
                    if barbell_angles['is_bar_level']:
                        tilt_text += " (LEVEL)"
                    cv2.putText(annotated_image, tilt_text, (10, y_offset),
                                cv2.FONT_HERSHEY_SIMPLEX, 1.0, tilt_color, 2)
                    y_offset += 40

                    # Barbell relative to body
                    offset = barbell_angles['forward_offset']
                    if offset > 20:
                        position_text = f"Bar Position: FORWARD ({abs(offset):.0f}px)"
                        pos_color = (0, 165, 255)  # Orange - forward
                    elif offset < -20:
                        position_text = f"Bar Position: BACK ({abs(offset):.0f}px)"
                        pos_color = (0, 165, 255)  # Orange - back
                    else:
                        position_text = f"Bar Position: CENTERED"
                        pos_color = (0, 255, 0)  # Green - centered
                    cv2.putText(annotated_image, position_text, (10, y_offset),
                                cv2.FONT_HERSHEY_SIMPLEX, 1.0, pos_color, 2)

                    # Add barbell angles to spine data
                    spine_data['barbell_angles'] = barbell_angles

    return annotated_image, spine_data, barbell_result


def rdp_simplify(points, epsilon):
    """
    Ramer-Douglas-Peucker algorithm for polyline simplification.
    Reduces the number of points in a curve while preserving its overall shape.

    Args:
        points: numpy array of shape (N, 2) — each row is (x, y)
        epsilon: maximum perpendicular distance threshold; larger values
                 produce more aggressive simplification

    Returns:
        numpy array of simplified points
    """
    if len(points) <= 2:
        return points

    # Find the point with the maximum distance from the line (first -> last)
    start = points[0]
    end = points[-1]

    # Vector from start to end
    line_vec = end - start
    line_len = np.linalg.norm(line_vec)

    if line_len == 0:
        # All points collapse to the same location — just keep endpoints
        return np.array([start, end])

    line_unit = line_vec / line_len

    # Perpendicular distances of every interior point to the start-end line
    vecs = points[1:-1] - start
    # Project onto line direction, then get perpendicular component
    projs = np.dot(vecs, line_unit)
    perp_vecs = vecs - np.outer(projs, line_unit)
    distances = np.linalg.norm(perp_vecs, axis=1)

    max_idx = np.argmax(distances) + 1  # +1 because we skipped index 0
    max_dist = distances[max_idx - 1]

    if max_dist > epsilon:
        # Recurse on both halves
        left = rdp_simplify(points[:max_idx + 1], epsilon)
        right = rdp_simplify(points[max_idx:], epsilon)
        return np.vstack([left[:-1], right])
    else:
        # All interior points are within tolerance — keep only endpoints
        return np.array([start, end])


def auto_template_split(signal):
    """
    Automatically split a signal into repetitions using the first detected
    cycle as a template.  Returns (split_indices, similarity_scores).
    split_indices are the sample indices where each new repetition starts.
    """
    smoothed = savgol_filter(signal, min(11, len(signal) // 2 * 2 + 1), 3)
    avg = np.mean(smoothed)

    # Find mean crossings (upwards) to roughly locate cycle boundaries
    crossings = np.where((smoothed[:-1] < avg) & (smoothed[1:] >= avg))[0]

    if len(crossings) < 2:
        return [], None

    # Use the first detected cycle as a draft template
    start_idx = crossings[0]
    end_idx = crossings[1]
    draft_template = smoothed[start_idx:end_idx]

    # Normalize the draft (Z-score)
    temp_norm = (draft_template - np.mean(draft_template)) / (np.std(draft_template) + 1e-6)
    t_len = len(temp_norm)

    # Slide the draft across the signal and compute Pearson correlation
    similarities = []
    for i in range(len(smoothed) - t_len):
        window = smoothed[i: i + t_len]
        std_win = np.std(window)
        if std_win < 1e-6:
            similarities.append(0)
            continue
        win_norm = (window - np.mean(window)) / std_win
        corr = np.mean(temp_norm * win_norm)
        similarities.append(corr)

    similarities = np.array(similarities)

    # Peak detection – each peak marks the start of a similar cycle
    refined_indices, _ = find_peaks(similarities, height=0.5,
                                    distance=int(t_len * 0.7))

    return refined_indices, similarities


def generate_angle_plots(time_data, left_leg_spine_angles, right_leg_spine_angles,
                         left_foot_leg_angles, right_foot_leg_angles,
                         left_knee_angles, right_knee_angles, video_path,
                         rdp_epsilon=1.5):
    """
    Generate and save plots for spine-leg angle vs time and foot-leg angle vs time.
    Raw data is shown faintly; the simplified RDP curve is drawn on top.

    Args:
        time_data: List of timestamps in seconds
        left_leg_spine_angles: List of left leg-spine angles (or None for missing frames)
        right_leg_spine_angles: List of right leg-spine angles (or None for missing frames)
        left_foot_leg_angles: List of left foot-leg angles (or None for missing frames)
        right_foot_leg_angles: List of right foot-leg angles (or None for missing frames)
        left_knee_angles: List of left knee angles (or None for missing frames)
        right_knee_angles: List of right knee angles (or None for missing frames)
        video_path: Path to the video file (used for naming output files)
        rdp_epsilon: RDP simplification tolerance (degrees). Larger = smoother.
    """
    # Filter out None values for plotting
    valid_left_leg_spine = [(t, a) for t, a in zip(time_data, left_leg_spine_angles) if a is not None]
    valid_right_leg_spine = [(t, a) for t, a in zip(time_data, right_leg_spine_angles) if a is not None]
    valid_left_foot = [(t, a) for t, a in zip(time_data, left_foot_leg_angles) if a is not None]
    valid_right_foot = [(t, a) for t, a in zip(time_data, right_foot_leg_angles) if a is not None]
    valid_left_knee = [(t, a) for t, a in zip(time_data, left_knee_angles) if a is not None]
    valid_right_knee = [(t, a) for t, a in zip(time_data, right_knee_angles) if a is not None]

    # --- Auto-split repetitions using the best available angle signal ---
    # Pick the longest valid signal among knee / leg-spine angles for splitting
    split_times = []
    candidate_signals = [
        ('left_knee', valid_left_knee),
        ('right_knee', valid_right_knee),
        ('left_leg_spine', valid_left_leg_spine),
        ('right_leg_spine', valid_right_leg_spine),
    ]
    best_signal_name = None
    best_signal_data = None
    for name, data in candidate_signals:
        if data and (best_signal_data is None or len(data) > len(best_signal_data)):
            best_signal_data = data
            best_signal_name = name

    if best_signal_data and len(best_signal_data) > 20:
        split_signal_times, split_signal_angles = zip(*best_signal_data)
        split_indices, _ = auto_template_split(np.array(split_signal_angles))
        if len(split_indices) > 0:
            split_times = [split_signal_times[i] for i in split_indices
                           if i < len(split_signal_times)]
            print(f"Auto-split: detected {len(split_times)} repetition(s) "
                  f"using '{best_signal_name}' signal")

    # Create figure with subplots
    fig, axes = plt.subplots(3, 1, figsize=(14, 12))
    fig.suptitle(f'Angle Analysis: {video_path.split("/")[-1]}', fontsize=14, fontweight='bold')

    # Plot 1: Left & Right Spine-Leg Angle vs Time
    if valid_left_leg_spine:
        times, angles = zip(*valid_left_leg_spine)
        pts = np.column_stack([times, angles])
        simplified = rdp_simplify(pts, rdp_epsilon)
        axes[0].plot(times, angles, 'b-', linewidth=0.5, alpha=0.3, label='Left raw')
        axes[0].plot(simplified[:, 0], simplified[:, 1], 'b-', linewidth=2,
                     label=f'Left RDP (eps={rdp_epsilon})')
        axes[0].scatter(simplified[:, 0], simplified[:, 1], c='blue', s=15, zorder=5)
    if valid_right_leg_spine:
        times, angles = zip(*valid_right_leg_spine)
        pts = np.column_stack([times, angles])
        simplified = rdp_simplify(pts, rdp_epsilon)
        axes[0].plot(times, angles, color='cyan', linewidth=0.5, alpha=0.3, label='Right raw')
        axes[0].plot(simplified[:, 0], simplified[:, 1], color='cyan', linewidth=2,
                     label=f'Right RDP (eps={rdp_epsilon})')
        axes[0].scatter(simplified[:, 0], simplified[:, 1], c='cyan', s=15, zorder=5)
    axes[0].axhline(y=180, color='g', linestyle='--', alpha=0.5, label='Straight (180\u00b0)')
    axes[0].axhline(y=90, color='r', linestyle='--', alpha=0.5, label='Right angle (90\u00b0)')
    all_leg_spine_times = ([t for t, _ in valid_left_leg_spine] +
                           [t for t, _ in valid_right_leg_spine])
    if all_leg_spine_times:
        axes[0].fill_between([min(all_leg_spine_times), max(all_leg_spine_times)],
                             160, 200, alpha=0.1, color='green', label='Good posture range')
    axes[0].set_xlabel('Time (seconds)')
    axes[0].set_ylabel('Angle (degrees)')
    axes[0].set_title('Spine-Leg Angle vs Time \u2014 Left & Right')
    axes[0].legend(loc='upper right')
    axes[0].grid(True, alpha=0.3)
    axes[0].set_ylim([0, 200])

    # Plot 2: Left & Right Foot-Leg Angle vs Time
    if valid_left_foot:
        times, angles = zip(*valid_left_foot)
        pts = np.column_stack([times, angles])
        simplified = rdp_simplify(pts, rdp_epsilon)
        axes[1].plot(times, angles, 'r-', linewidth=0.5, alpha=0.3, label='Left raw')
        axes[1].plot(simplified[:, 0], simplified[:, 1], 'r-', linewidth=2,
                     label=f'Left RDP (eps={rdp_epsilon})')
        axes[1].scatter(simplified[:, 0], simplified[:, 1], c='red', s=15, zorder=5)
    if valid_right_foot:
        times, angles = zip(*valid_right_foot)
        pts = np.column_stack([times, angles])
        simplified = rdp_simplify(pts, rdp_epsilon)
        axes[1].plot(times, angles, color='orange', linewidth=0.5, alpha=0.3, label='Right raw')
        axes[1].plot(simplified[:, 0], simplified[:, 1], color='orange', linewidth=2,
                     label=f'Right RDP (eps={rdp_epsilon})')
        axes[1].scatter(simplified[:, 0], simplified[:, 1], c='orange', s=15, zorder=5)
    axes[1].axhline(y=90, color='g', linestyle='--', alpha=0.5, label='Neutral (90\u00b0)')
    axes[1].set_xlabel('Time (seconds)')
    axes[1].set_ylabel('Angle (degrees)')
    axes[1].set_title('Foot-Leg Angle vs Time (Ankle Dorsiflexion) \u2014 Left & Right')
    axes[1].legend(loc='upper right')
    axes[1].grid(True, alpha=0.3)

    # Plot 3: Left & Right Knee Angle vs Time
    if valid_left_knee:
        times, angles = zip(*valid_left_knee)
        pts = np.column_stack([times, angles])
        simplified = rdp_simplify(pts, rdp_epsilon)
        axes[2].plot(times, angles, 'g-', linewidth=0.5, alpha=0.3, label='Left raw')
        axes[2].plot(simplified[:, 0], simplified[:, 1], 'g-', linewidth=2,
                     label=f'Left RDP (eps={rdp_epsilon})')
        axes[2].scatter(simplified[:, 0], simplified[:, 1], c='green', s=15, zorder=5)
    if valid_right_knee:
        times, angles = zip(*valid_right_knee)
        pts = np.column_stack([times, angles])
        simplified = rdp_simplify(pts, rdp_epsilon)
        axes[2].plot(times, angles, color='lime', linewidth=0.5, alpha=0.3, label='Right raw')
        axes[2].plot(simplified[:, 0], simplified[:, 1], color='lime', linewidth=2,
                     label=f'Right RDP (eps={rdp_epsilon})')
        axes[2].scatter(simplified[:, 0], simplified[:, 1], c='lime', s=15, zorder=5)
    axes[2].axhline(y=180, color='b', linestyle='--', alpha=0.5, label='Straight (180°)')
    axes[2].axhline(y=90, color='r', linestyle='--', alpha=0.5, label='Bent 90°')
    axes[2].set_xlabel('Time (seconds)')
    axes[2].set_ylabel('Angle (degrees)')
    axes[2].set_title('Knee Angle vs Time — Left & Right')
    axes[2].legend(loc='upper right')
    axes[2].grid(True, alpha=0.3)
    axes[2].set_ylim([0, 200])

    # --- Draw split lines on all subplots ---
    for i, ax in enumerate(axes):
        for si, st in enumerate(split_times):
            ax.axvline(x=st, color='red', linestyle='--', linewidth=1.2, alpha=0.7,
                       label='Rep split' if (si == 0 and i == 0) else '')
            if i == 0:  # label only on top subplot
                ax.text(st, ax.get_ylim()[1] * 0.97, f'R{si + 1}',
                        ha='center', va='top', fontsize=8, color='red',
                        fontweight='bold')
    if split_times:
        axes[0].legend(loc='upper right')

    plt.tight_layout()

    # Save the plot
    plot_path = video_path.replace('.mp4', '_angle_plots.png')
    plt.savefig(plot_path, dpi=150, bbox_inches='tight')
    print(f"Angle plots saved to: {plot_path}")


    # Close the figure to free memory
    plt.close(fig)

    # Print statistics
    if valid_left_leg_spine:
        _, angles = zip(*valid_left_leg_spine)
        print(f"\nLeft Spine-Leg Angle Statistics:")
        print(f"  Min: {min(angles):.1f}\u00b0, Max: {max(angles):.1f}\u00b0, Mean: {np.mean(angles):.1f}\u00b0")

    if valid_right_leg_spine:
        _, angles = zip(*valid_right_leg_spine)
        print(f"\nRight Spine-Leg Angle Statistics:")
        print(f"  Min: {min(angles):.1f}\u00b0, Max: {max(angles):.1f}\u00b0, Mean: {np.mean(angles):.1f}\u00b0")

    if valid_left_foot:
        _, angles = zip(*valid_left_foot)
        print(f"\nLeft Foot-Leg Angle Statistics:")
        print(f"  Min: {min(angles):.1f}\u00b0, Max: {max(angles):.1f}\u00b0, Mean: {np.mean(angles):.1f}\u00b0")

    if valid_right_foot:
        _, angles = zip(*valid_right_foot)
        print(f"\nRight Foot-Leg Angle Statistics:")
        print(f"  Min: {min(angles):.1f}\u00b0, Max: {max(angles):.1f}\u00b0, Mean: {np.mean(angles):.1f}\u00b0")

    if valid_left_knee:
        _, angles = zip(*valid_left_knee)
        print(f"\nLeft Knee Angle Statistics:")
        print(f"  Min: {min(angles):.1f}\u00b0, Max: {max(angles):.1f}\u00b0, Mean: {np.mean(angles):.1f}\u00b0")

    if valid_right_knee:
        _, angles = zip(*valid_right_knee)
        print(f"\nRight Knee Angle Statistics:")
        print(f"  Min: {min(angles):.1f}\u00b0, Max: {max(angles):.1f}\u00b0, Mean: {np.mean(angles):.1f}\u00b0")


def generate_confidence_plot(time_data, confidence_data, confidence_details_data,
                             video_path, rdp_epsilon=0.02):
    """
    Generate and save a barbell-detection confidence plot.

    Args:
        time_data: list of timestamps (seconds)
        confidence_data: list of confidence values (0.0-1.0) or None per frame
        confidence_details_data: list of detail dicts (or None) per frame
        video_path: original video path (used for naming)
        rdp_epsilon: RDP tolerance for the overall confidence curve
    """
    # --- Prepare valid points ---
    valid = [(t, c) for t, c in zip(time_data, confidence_data) if c is not None]
    if not valid:
        print("No barbell confidence data to plot.")
        return

    times_all, confs_all = zip(*valid)

    # Collect per-factor time series
    factor_keys = ['line_length', 'proximity', 'tilt', 'score_margin',
                   'n_candidates', 'temporal', 'method', 'wrist_count']
    factor_series = {k: [] for k in factor_keys}
    factor_times = {k: [] for k in factor_keys}
    for t, det in zip(time_data, confidence_details_data):
        if det is None:
            continue
        for k in factor_keys:
            if k in det:
                factor_times[k].append(t)
                factor_series[k].append(det[k])

    # --- Create figure: 2 subplots ---
    fig, axes = plt.subplots(2, 1, figsize=(14, 9),
                             gridspec_kw={'height_ratios': [2, 1.2]})
    fig.suptitle(f'Barbell Detection Confidence: {video_path.split("/")[-1]}',
                 fontsize=14, fontweight='bold')

    # ===== Top plot: overall confidence =====
    ax = axes[0]
    # Raw line (faint)
    ax.plot(times_all, confs_all, color='dodgerblue', linewidth=0.6, alpha=0.35,
            label='Raw confidence')
    # RDP-simplified line
    pts = np.column_stack([times_all, confs_all])
    simplified = rdp_simplify(pts, rdp_epsilon)
    ax.plot(simplified[:, 0], simplified[:, 1], color='dodgerblue', linewidth=2,
            label=f'Smoothed (RDP eps={rdp_epsilon})')
    ax.scatter(simplified[:, 0], simplified[:, 1], c='dodgerblue', s=12, zorder=5)

    # Threshold bands
    ax.axhspan(0.7, 1.0, color='green', alpha=0.08, label='High (>=70%)')
    ax.axhspan(0.4, 0.7, color='orange', alpha=0.08, label='Medium (40-70%)')
    ax.axhspan(0.0, 0.4, color='red', alpha=0.08, label='Low (<40%)')
    ax.axhline(y=0.7, color='green', linestyle='--', linewidth=0.8, alpha=0.5)
    ax.axhline(y=0.4, color='orange', linestyle='--', linewidth=0.8, alpha=0.5)

    # Detection gaps (frames with no barbell detected)
    gap_times = [t for t, c in zip(time_data, confidence_data) if c is None]
    if gap_times:
        ax.scatter(gap_times, [0.0] * len(gap_times), color='red', marker='x',
                   s=10, alpha=0.4, label='No detection')

    ax.set_ylabel('Confidence (0-1)')
    ax.set_ylim([-0.05, 1.05])
    ax.set_title('Overall Confidence vs Time')
    ax.legend(loc='lower right', fontsize=8)
    ax.grid(True, alpha=0.3)

    # Stats annotation
    mean_c = np.mean(confs_all)
    med_c = np.median(confs_all)
    pct_high = sum(1 for c in confs_all if c >= 0.7) / len(confs_all) * 100
    pct_detect = len(valid) / len(time_data) * 100
    stats_text = (f"Mean={mean_c:.2f}  Median={med_c:.2f}  "
                  f">=70%: {pct_high:.0f}%  Detected: {pct_detect:.0f}% of frames")
    ax.text(0.01, 0.97, stats_text, transform=ax.transAxes,
            fontsize=8, va='top', ha='left',
            bbox=dict(boxstyle='round,pad=0.3', fc='white', alpha=0.8))

    # ===== Bottom plot: per-factor breakdown =====
    ax2 = axes[1]
    factor_colors = {
        'line_length': 'tab:blue',
        'proximity': 'tab:orange',
        'tilt': 'tab:green',
        'score_margin': 'tab:red',
        'n_candidates': 'tab:purple',
        'temporal': 'tab:brown',
        'method': 'tab:gray',
        'wrist_count': 'tab:pink',
    }
    factor_labels = {
        'line_length': 'Line Length',
        'proximity': 'Proximity',
        'tilt': 'Tilt',
        'score_margin': 'Score Margin',
        'n_candidates': '# Candidates',
        'temporal': 'Temporal',
        'method': 'Method',
        'wrist_count': 'Wrist Count',
    }
    for k in factor_keys:
        if factor_series[k]:
            ax2.plot(factor_times[k], factor_series[k],
                     color=factor_colors[k], linewidth=1, alpha=0.7,
                     label=factor_labels[k])
    ax2.set_xlabel('Time (seconds)')
    ax2.set_ylabel('Sub-score (0-1)')
    ax2.set_ylim([-0.05, 1.15])
    ax2.set_title('Confidence Factor Breakdown')
    ax2.legend(loc='upper right', fontsize=7, ncol=4)
    ax2.grid(True, alpha=0.3)

    plt.tight_layout()

    # Save
    plot_path = video_path.replace('.mp4', '_barbell_confidence.png')
    plt.savefig(plot_path, dpi=150, bbox_inches='tight')
    plt.close(fig)
    print(f"Barbell confidence plot saved to: {plot_path}")

    # Print summary
    print(f"\nBarbell Confidence Statistics:")
    print(f"  Frames with detection: {len(valid)} / {len(time_data)} ({pct_detect:.1f}%)")
    print(f"  Mean confidence: {mean_c:.3f}")
    print(f"  Median confidence: {med_c:.3f}")
    print(f"  Min: {min(confs_all):.3f}, Max: {max(confs_all):.3f}")
    print(f"  Frames >= 70% confidence: {pct_high:.1f}%")


def process_video(video_path, model_path):
    """Process a video file with pose landmark detection."""

    # ---- set up detector depending on backend ----
    if USE_GPU:
        yolo_model = _YOLO("yolov8m-pose.pt")
        device = 0 if torch.cuda.is_available() else "cpu"
        print(f"YOLO-Pose model loaded — device: {device}")
    else:
        import mediapipe as mp
        base_options = python.BaseOptions(
            model_asset_path=model_path,
            delegate=python.BaseOptions.Delegate.CPU
        )
        options = vision.PoseLandmarkerOptions(
            base_options=base_options,
            output_segmentation_masks=False,
            running_mode=vision.RunningMode.VIDEO
        )
        detector = vision.PoseLandmarker.create_from_options(options)
        print("MediaPipe CPU backend")

    # Open video file
    cap = cv2.VideoCapture(video_path)

    if not cap.isOpened():
        print(f"Error: Could not open video {video_path}")
        return

    fps = cap.get(cv2.CAP_PROP_FPS)
    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    print(f"Processing video: {video_path}")
    print(f"Resolution: {frame_width}x{frame_height}, FPS: {fps}")

    # Create output video writer
    if USE_GPU:
        output_path = video_path.replace('.mp4', '_landmarks_yolo_gpu.mp4')
    else:
        output_path = video_path.replace('.mp4', '_landmarks_' + model_path.split(".")[0] + '.mp4')
    fourcc = cv2.VideoWriter.fourcc(*'mp4v')
    out = cv2.VideoWriter(output_path, fourcc, fps, (frame_width, frame_height))


    frame_count = 0

    # Data collection for angle plots
    time_data = []
    left_leg_spine_angles = []
    right_leg_spine_angles = []
    left_foot_leg_angles = []
    right_foot_leg_angles = []
    left_knee_angles = []
    right_knee_angles = []
    # Data collection for barbell confidence plot
    barbell_confidence_data = []
    barbell_confidence_details_data = []

    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break

        # Convert BGR to RGB
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        # ---- detect ----
        if USE_GPU:
            results = yolo_model.predict(rgb_frame, device=device, verbose=False)
            detection_result = _yolo_to_detection_result(results, frame_height, frame_width)
        else:
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
            timestamp_ms = int(frame_count * 1000 / fps)
            detection_result = detector.detect_for_video(mp_image, timestamp_ms)

        # Draw landmarks on the frame (also returns spine analysis data and barbell result)
        annotated_frame, spine_data, barbell_result = draw_landmarks_on_image(rgb_frame, detection_result)

        # Collect angle data for plotting
        time_data.append(frame_count / fps)  # Time in seconds
        if spine_data is not None:
            left_leg_spine_angles.append(spine_data.get('left_leg_spine_angle'))
            right_leg_spine_angles.append(spine_data.get('right_leg_spine_angle'))
            left_foot_leg_angles.append(spine_data.get('left_foot_leg_angle'))
            right_foot_leg_angles.append(spine_data.get('right_foot_leg_angle'))
            left_knee_angles.append(spine_data.get('left_knee_angle'))
            right_knee_angles.append(spine_data.get('right_knee_angle'))
        else:
            left_leg_spine_angles.append(None)
            right_leg_spine_angles.append(None)
            left_foot_leg_angles.append(None)
            right_foot_leg_angles.append(None)
            left_knee_angles.append(None)
            right_knee_angles.append(None)

        # Collect barbell confidence data
        if (barbell_result is not None and barbell_result.get('barbell') is not None):
            barbell = barbell_result['barbell']
            barbell_confidence_data.append(barbell.get('confidence'))
            barbell_confidence_details_data.append(barbell.get('confidence_details'))
        else:
            barbell_confidence_data.append(None)
            barbell_confidence_details_data.append(None)

        # Convert back to BGR for OpenCV
        bgr_annotated = cv2.cvtColor(annotated_frame, cv2.COLOR_RGB2BGR)

        # Write to output video
        out.write(bgr_annotated)

        frame_count += 1
        if frame_count % 30 == 0:
            print(f"Processed {frame_count} frames...")

    # Cleanup
    cap.release()
    out.release()
    if not USE_GPU:
        detector.close()

    print(f"Done! Output saved to: {output_path}")
    print(f"Total frames processed: {frame_count}")

    # Generate angle plots
    if time_data:
        print("\nGenerating angle plots...")
        generate_angle_plots(time_data, left_leg_spine_angles, right_leg_spine_angles,
                             left_foot_leg_angles, right_foot_leg_angles,
                             left_knee_angles, right_knee_angles,
                             video_path, 0.4)

    # Generate barbell confidence plot
    if time_data and BARBELL_DETECTION_ENABLED:
        print("\nGenerating barbell confidence plot...")
        generate_confidence_plot(time_data, barbell_confidence_data,
                                 barbell_confidence_details_data,
                                 video_path, rdp_epsilon=0.02)


def main():
    # Model paths (used only for MediaPipe CPU backend)
    model_lite = "pose_landmarker_lite.task"
    model_full = "pose_landmarker_full.task"
    model_heavy = "pose_landmarker_heavy.task"

    # Video files
    videos = ["petal_20260130_195005.mp4", "petal_20260130_195147.mp4", "petal_20260204_195052.mp4",
              "petal_20260204_195517.mp4", "petal_20260204_200033.mp4"]
    videos = ["Zhao Jinhong (45kg) snatching 90kg — double bodyweight!_2160p.mp4"]
    videos = ["cut_wl.mp4"]
    # videos = ["Zhao Jinhong (45kg, China 🇨🇳) with a new C&J world record of 113kg249lb at the 2024 WWC!_1440p.mp4"]
    # videos = ["petal_20260313_193615.mp4"]
    videos = ["2026-03-14 22-57-33.mp4"]
    videos = ["2026-03-15 00-20-26.mp4"]
    videos = ["petal_20260313_140005.mp4"]
    videos = ["petal_20260320_194209.mp4", "petal_20260320_200055.mp4"]

    # Use the full model for good balance of speed/accuracy
    selected_model = model_full

    print("=" * 50)
    print("Pose Landmark Detection")
    print("=" * 50)
    backend = "YOLO-Pose GPU" if USE_GPU else f"MediaPipe CPU ({selected_model})"
    print(f"Backend: {backend}")
    print("=" * 50)

    for index, video in enumerate(videos):
        print(f"\n[Processing Video {index + 1} of {len(videos)}]")
        process_video(video, selected_model)

    print("\n" + "=" * 50)
    print("All videos processed!")
    print("=" * 50)


if __name__ == "__main__":
    main()
