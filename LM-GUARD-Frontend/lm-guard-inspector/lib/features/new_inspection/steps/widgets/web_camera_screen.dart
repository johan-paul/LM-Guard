import 'package:camera/camera.dart';
import 'package:flutter/material.dart';

/// Full-screen live camera preview + capture button.
///
/// Used only on Flutter Web (see [CameraEvidenceService] in evidence_service.dart) - desktop
/// browsers have no OS-level "take a photo now" integration in the plain file-open dialog that
/// `image_picker`'s camera source relies on there (unlike Android/iOS, where that source really
/// does open the native camera app), so on the web it silently fell back to an ordinary file
/// picker. This drives `getUserMedia()` directly through the `camera` package for a real live
/// preview instead.
///
/// Pops with the captured [XFile], or `null` if the officer backs out without taking a photo.
class WebCameraScreen extends StatefulWidget {
  const WebCameraScreen({super.key, required this.label});

  final String label;

  @override
  State<WebCameraScreen> createState() => _WebCameraScreenState();
}

class _WebCameraScreenState extends State<WebCameraScreen> {
  CameraController? _controller;
  String? _error;
  bool _capturing = false;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    setState(() => _error = null);
    try {
      final List<CameraDescription> cameras = await availableCameras();
      if (cameras.isEmpty) {
        if (mounted) setState(() => _error = 'No camera was found on this device.');
        return;
      }
      // Prefer a back/environment-facing camera when the browser reports lens direction - what
      // an inspector actually wants for photographing a package, not a front-facing selfie
      // camera. Most laptops only expose one undirected camera, so this falls back to it.
      final CameraDescription camera = cameras.firstWhere(
        (CameraDescription c) => c.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );
      final CameraController controller = CameraController(
        camera,
        ResolutionPreset.high,
        enableAudio: false,
      );
      await controller.initialize();
      if (!mounted) {
        await controller.dispose();
        return;
      }
      setState(() => _controller = controller);
    } catch (e) {
      if (mounted) setState(() => _error = _describeError(e));
    }
  }

  String _describeError(Object e) {
    final String text = e.toString();
    if (text.contains('NotAllowedError') || text.contains('Permission')) {
      return 'Camera access was denied. Allow camera permission for this site in the '
          'browser (check the address bar) and try again.';
    }
    if (text.contains('NotFoundError') || text.contains('OverconstrainedError')) {
      return 'No camera was found on this device.';
    }
    if (text.contains('NotReadableError')) {
      return 'The camera could not be started - it may already be in use by another '
          'application or browser tab.';
    }
    return 'Could not start the camera: $text';
  }

  Future<void> _capture() async {
    final CameraController? controller = _controller;
    if (controller == null || !controller.value.isInitialized || _capturing) return;
    setState(() => _capturing = true);
    try {
      final XFile file = await controller.takePicture();
      if (mounted) Navigator.of(context).pop(file);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = 'Could not capture the photo: $e';
          _capturing = false;
        });
      }
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        elevation: 0,
        title: Text(widget.label),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_error != null) {
      return _ErrorState(message: _error!, onRetry: _init);
    }
    final CameraController? controller = _controller;
    if (controller == null || !controller.value.isInitialized) {
      return const Center(child: CircularProgressIndicator(color: Colors.white));
    }
    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        Center(child: CameraPreview(controller)),
        Positioned(
          left: 0,
          right: 0,
          bottom: 28,
          child: Center(
            child: GestureDetector(
              onTap: _capture,
              child: Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: _capturing ? Colors.white38 : Colors.white,
                  border: Border.all(color: Colors.white54, width: 4),
                ),
                child: _capturing
                    ? const Padding(
                        padding: EdgeInsets.all(20),
                        child: CircularProgressIndicator(strokeWidth: 2.5),
                      )
                    : null,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const Icon(Icons.videocam_off_outlined, color: Colors.white54, size: 40),
            const SizedBox(height: 12),
            Text(
              message,
              style: const TextStyle(color: Colors.white),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            OutlinedButton(
              onPressed: onRetry,
              style: OutlinedButton.styleFrom(foregroundColor: Colors.white),
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }
}
