import cv2
import numpy as np

# Image dimensions
width, height = 640, 480
image = np.ones((height, width, 3), dtype=np.uint8) * 255  # White background

# 1. Draw Green Artifact (Circle)
# Green in BGR is approx (0, 255, 0)
# But we need to match HSV range (35-85, 50-255, 50-255)
# Pure Green (0, 255, 0) -> HSV(60, 255, 255) which is perfect.
cv2.circle(image, (150, 240), 40, (0, 255, 0), -1)
cv2.putText(image, "Green Artifact", (100, 190), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1)

# 2. Draw Purple Artifact (Circle)
# Purple in HSV (130-165, ...)
# Try BGR (255, 0, 255) -> Magenta -> HSV(150, 255, 255) which is perfect.
cv2.circle(image, (300, 240), 40, (255, 0, 255), -1)
cv2.putText(image, "Purple Artifact", (250, 190), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1)

# 3. Draw Blue Alliance Robot (Rectangle)
# Blue in HSV (100-130, ...)
# Pure Blue (255, 0, 0) is BGR -> HSV(120, 255, 255).
cv2.rectangle(image, (450, 200), (530, 280), (255, 0, 0), -1)
cv2.putText(image, "Blue Robot", (450, 190), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1)

# 4. Generate ArUco Marker ID 21 (DICT_APRILTAG_36h11)
try:
    # Attempt to use DICT_APRILTAG_36h11
    aruco_dict = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_APRILTAG_36h11)
    # 100x100 pixels
    tag_img = cv2.aruco.generateImageMarker(aruco_dict, 21, 100)
    
    # Convert tag to BGR to place on image
    tag_img_bgr = cv2.cvtColor(tag_img, cv2.COLOR_GRAY2BGR)
    
    # Place tag at bottom center
    x_offset = (width - 100) // 2
    y_offset = 350
    image[y_offset:y_offset+100, x_offset:x_offset+100] = tag_img_bgr
    cv2.putText(image, "ID 21 (GPP)", (x_offset, y_offset - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1)
    
except Exception as e:
    print(f"Error generating ArUco marker: {e}")
    cv2.putText(image, "ArUco Gen Failed", (250, 400), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 1)

# Save the image
filename = "ftc_mock_test.jpg"
cv2.imwrite(filename, image)
print(f"Generated test image: {filename}")
